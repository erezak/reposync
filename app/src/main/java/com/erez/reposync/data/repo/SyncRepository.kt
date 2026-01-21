package com.erez.reposync.data.repo

import android.content.Context
import android.net.Uri
import com.erez.reposync.data.crypto.CryptoStore
import com.erez.reposync.data.db.dao.FingerprintDao
import com.erez.reposync.data.db.dao.SyncLogDao
import com.erez.reposync.data.db.entities.FingerprintEntity
import com.erez.reposync.data.db.entities.SyncLogEntity
import com.erez.reposync.data.github.GitHubAuthKeys
import com.erez.reposync.data.git.GitClient
import com.erez.reposync.data.git.SshHostKeyFetcher
import com.erez.reposync.data.model.AuthMethod
import com.erez.reposync.data.model.MirrorDiff
import com.erez.reposync.data.model.Profile
import com.erez.reposync.data.model.SyncResult
import com.erez.reposync.data.model.SyncStatus
import com.erez.reposync.data.model.SyncSummary
import com.erez.reposync.data.saf.SafRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import kotlin.math.max

class SyncRepository(
    private val appContext: Context,
    private val fingerprintDao: FingerprintDao,
    private val syncLogDao: SyncLogDao,
    private val profileRepository: ProfileRepository,
    private val safRepository: SafRepository,
    private val gitClient: GitClient,
    private val cryptoStore: CryptoStore
) {
    private val hostKeyFetcher = SshHostKeyFetcher()
    private val mirrorEngine = MirrorEngine(appContext, safRepository, fingerprintDao)
    private val mutexes = mutableMapOf<String, Mutex>()

    fun observeLogs(profileId: String): Flow<List<SyncLogEntity>> = syncLogDao.observeForProfile(profileId)

    suspend fun setupClone(profile: Profile, onStep: ((String) -> Unit)? = null): SyncResult = withContext(Dispatchers.IO) {
        val repoDir = gitClient.ensureRepoDir(profile.id)
        if (repoDir.exists()) {
            repoDir.deleteRecursively()
            repoDir.mkdirs()
        }
        val logWriter = SyncLogWriter(appContext, profile, cryptoStore, onStep)
        val startedAt = Instant.now()
        return@withContext runSync(profile, startedAt, logWriter) {
            logWriter.log("Cloning repository...")
            val git = timed(logWriter, "Clone") { gitClient.cloneRepository(profile, repoDir) }
            timed(logWriter, "Checkout") { gitClient.checkoutBranch(git, profile.branch) }
            updateExcludeFile(profile, repoDir)
            val outbound = timed(logWriter, "Mirror internal → target") {
                mirrorEngine.mirrorInternalToTarget(profile, git.repository.workTree, logWriter)
            }
            logWriter.setSummary(
                SyncSummary(outbound.added.size, outbound.modified.size, outbound.deleted.size, 0)
            )
            null
        }
    }

    suspend fun setupImport(profile: Profile, onStep: ((String) -> Unit)? = null): SyncResult = withContext(Dispatchers.IO) {
        val repoDir = gitClient.ensureRepoDir(profile.id)
        if (repoDir.exists()) {
            repoDir.deleteRecursively()
            repoDir.mkdirs()
        }
        val logWriter = SyncLogWriter(appContext, profile, cryptoStore, onStep)
        val startedAt = Instant.now()
        return@withContext runSync(profile, startedAt, logWriter) {
            logWriter.log("Initializing repository...")
            val git = timed(logWriter, "Init repo") { gitClient.initRepository(repoDir, profile.branch) }
            timed(logWriter, "Set remote") { gitClient.setRemote(git, profile.remoteUrl) }
            val diff = timed(logWriter, "Mirror target → internal") {
                mirrorEngine.mirrorTargetToInternal(profile, git.repository.workTree, logWriter)
            }
            updateExcludeFile(profile, repoDir)
            logWriter.log("Staging files...")
            timed(logWriter, "Stage") { gitClient.stageAll(git) }
            val committed = timed(logWriter, "Commit") {
                gitClient.commitIfNeeded(git, profile.authorName, profile.authorEmail, profile.commitMessageTemplate)
            }
            if (committed) logWriter.log("Committed ${diff.added.size + diff.modified.size} changes")
            logWriter.log("Pushing to remote...")
            timed(logWriter, "Push") { gitClient.push(git, profile) }
            val outbound = timed(logWriter, "Mirror internal → target") {
                mirrorEngine.mirrorInternalToTarget(profile, git.repository.workTree, logWriter)
            }
            logWriter.setSummary(
                SyncSummary(
                    added = diff.added.size + outbound.added.size,
                    modified = diff.modified.size + outbound.modified.size,
                    deleted = diff.deleted.size + outbound.deleted.size,
                    conflicts = 0
                )
            )
            null
        }
    }

    suspend fun syncNow(
        profileId: String,
        mode: SyncMode = SyncMode.FULL,
        onStep: ((String) -> Unit)? = null
    ): SyncResult {
        val profile = profileRepository.getById(profileId) ?: return SyncResult(
            profileId = profileId,
            status = SyncStatus.FAILED,
            startedAt = Instant.now(),
            finishedAt = Instant.now(),
            summary = SyncSummary(0, 0, 0, 0),
            errorMessage = "Profile not found"
        )
        val mutex = mutexes.getOrPut(profileId) { Mutex() }
        return mutex.withLock {
            val logWriter = SyncLogWriter(appContext, profile, cryptoStore, onStep)
            val startedAt = Instant.now()
            return@withLock runSync(profile, startedAt, logWriter) {
                val repoDir = gitClient.ensureRepoDir(profile.id)
                if (!gitClient.hasRepo(profile.id)) {
                    throw IllegalStateException("Repository not initialized. Run setup first.")
                }
                val git = gitClient.openRepository(repoDir)
                gitClient.validateSshKnownHost(profile)
                logWriter.log("Mirror target → internal...")
                val inbound = timed(logWriter, "Mirror target → internal") {
                    mirrorEngine.mirrorTargetToInternal(profile, git.repository.workTree, logWriter)
                }
                updateExcludeFile(profile, repoDir)
                logWriter.log("Staging changes...")
                timed(logWriter, "Stage") { gitClient.stageAll(git) }
                timed(logWriter, "Commit") {
                    gitClient.commitIfNeeded(git, profile.authorName, profile.authorEmail, profile.commitMessageTemplate)
                }
                if (mode != SyncMode.PUSH_ONLY) {
                    logWriter.log("Pulling from remote...")
                    val pull = timed(logWriter, "Pull") { gitClient.pullRebase(git, profile) }
                    val conflicts = pull.mergeResult?.conflicts?.size ?: 0
                    if (!pull.isSuccessful || conflicts > 0) {
                        val conflictFiles = git.status().call().conflicting.toList()
                        if (conflictFiles.isNotEmpty()) {
                            logWriter.log("Conflicting files:")
                            conflictFiles.forEach { logWriter.log("- $it") }
                        }
                        logWriter.log("Conflicts detected. Resolve and retry.")
                        return@runSync SyncResult(
                            profileId = profile.id,
                            status = SyncStatus.CONFLICT,
                            startedAt = startedAt,
                            finishedAt = Instant.now(),
                            summary = SyncSummary(inbound.added.size, inbound.modified.size, inbound.deleted.size, conflicts),
                            logPath = logWriter.closeAndGetPath(),
                            errorMessage = "Conflicts detected"
                        ).also { saveLog(it) }
                    }
                }
                if (mode != SyncMode.PULL_ONLY) {
                    logWriter.log("Pushing to remote...")
                    timed(logWriter, "Push") { gitClient.push(git, profile) }
                }
                logWriter.log("Mirror internal → target...")
                val outbound = timed(logWriter, "Mirror internal → target") {
                    mirrorEngine.mirrorInternalToTarget(profile, git.repository.workTree, logWriter)
                }
                val summary = SyncSummary(
                    added = inbound.added.size + outbound.added.size,
                    modified = inbound.modified.size + outbound.modified.size,
                    deleted = inbound.deleted.size + outbound.deleted.size,
                    conflicts = 0
                )
                logWriter.log("Summary: +${summary.added}, ~${summary.modified}, -${summary.deleted}")
                logWriter.setSummary(summary)
                null
            }
        }
    }

    suspend fun testConnection(profile: Profile): TestConnectionResult = withContext(Dispatchers.IO) {
        return@withContext try {
            if (profile.authMethod == AuthMethod.SSH_KEY) {
                val host = gitClient.parseRemoteHost(profile.remoteUrl)
                    ?: return@withContext TestConnectionResult.Failure("Unable to parse SSH host")
                if (!gitClient.getKnownHostsStore().isTrusted(host.host, host.port)) {
                    val info = hostKeyFetcher.fetch(host.host, host.port)
                    return@withContext TestConnectionResult.HostKeyNotTrusted(info)
                }
            }
            gitClient.lsRemote(profile)
            TestConnectionResult.Success
        } catch (ex: Exception) {
            val message = ex.message ?: "Connection failed"
            val cause = ex.cause?.message
            val combined = if (cause.isNullOrBlank() || cause == message) message else "$message (cause: $cause)"
            TestConnectionResult.Failure(combined)
        }
    }

    suspend fun trustHostKey(info: SshHostKeyFetcher.HostKeyInfo) {
        gitClient.getKnownHostsStore().addHost(info.host, info.port, info.keyType, info.keyBase64)
    }

    suspend fun deleteProfile(profileId: String, deleteContent: Boolean) = withContext(Dispatchers.IO) {
        val profile = profileRepository.getById(profileId) ?: return@withContext
        if (deleteContent) {
            val uri = Uri.parse(profile.targetTreeUri)
            val root = safRepository.getTreeDocument(uri)
            if (root != null) {
                deleteTreeContents(root)
            }
        }
        gitClient.repoDirFor(profileId).deleteRecursively()
        fingerprintDao.deleteForProfile(profileId)
        syncLogDao.deleteForProfile(profileId)
        profileRepository.delete(profileId)
    }

    private suspend fun runSync(
        profile: Profile,
        startedAt: Instant,
        logWriter: SyncLogWriter,
        block: suspend () -> SyncResult?
    ): SyncResult {
        return try {
            val earlyResult = block()
            if (earlyResult != null) {
                return earlyResult
            }
            val result = SyncResult(
                profileId = profile.id,
                status = SyncStatus.SUCCESS,
                startedAt = startedAt,
                finishedAt = Instant.now(),
                summary = logWriter.summary,
                logPath = logWriter.closeAndGetPath()
            )
            saveLog(result)
            result
        } catch (ex: Exception) {
            logWriter.log("Error: ${formatThrowable(ex)}")
            val result = SyncResult(
                profileId = profile.id,
                status = SyncStatus.FAILED,
                startedAt = startedAt,
                finishedAt = Instant.now(),
                summary = logWriter.summary,
                logPath = logWriter.closeAndGetPath(),
                errorMessage = ex.message
            )
            saveLog(result)
            result
        }
    }

    private fun formatThrowable(ex: Throwable): String {
        val message = ex.message ?: ex.javaClass.simpleName
        val cause = ex.cause?.message
        return if (cause.isNullOrBlank() || cause == message) {
            message
        } else {
            "$message (cause: $cause)"
        }
    }

    private fun deleteTreeContents(root: androidx.documentfile.provider.DocumentFile) {
        root.listFiles().forEach { child ->
            if (child.isDirectory) {
                deleteTreeContents(child)
            }
            child.delete()
        }
    }

    private suspend fun <T> timed(logWriter: SyncLogWriter, label: String, block: suspend () -> T): T {
        val start = System.currentTimeMillis()
        logWriter.step(label)
        val result = block()
        val elapsed = System.currentTimeMillis() - start
        logWriter.log("$label: done (${elapsed}ms)")
        return result
    }

    private suspend fun saveLog(result: SyncResult) {
        val entity = SyncLogEntity(
            id = UUID.randomUUID().toString(),
            profileId = result.profileId,
            status = result.status.name,
            startedAtEpochMillis = result.startedAt.toEpochMilli(),
            finishedAtEpochMillis = result.finishedAt.toEpochMilli(),
            summaryAdded = result.summary.added,
            summaryModified = result.summary.modified,
            summaryDeleted = result.summary.deleted,
            summaryConflicts = result.summary.conflicts,
            logPath = result.logPath,
            errorMessage = result.errorMessage
        )
        syncLogDao.insert(entity)
    }

    private fun updateExcludeFile(profile: Profile, repoDir: File) {
        val exclude = File(repoDir, ".git/info/exclude")
        exclude.parentFile?.mkdirs()
        val lines = IgnoreMatcher.defaultPatterns() + profile.ignoreRules.patterns
        exclude.writeText(lines.joinToString("\n"))
    }
}

enum class SyncMode {
    FULL,
    PULL_ONLY,
    PUSH_ONLY
}

sealed class TestConnectionResult {
    data object Success : TestConnectionResult()
    data class HostKeyNotTrusted(val info: SshHostKeyFetcher.HostKeyInfo) : TestConnectionResult()
    data class Failure(val message: String) : TestConnectionResult()
}

class MirrorEngine(
    private val context: Context,
    private val safRepository: SafRepository,
    private val fingerprintDao: FingerprintDao
) {
    suspend fun mirrorTargetToInternal(profile: Profile, internalRoot: File, logWriter: SyncLogWriter): MirrorDiff {
        val treeUri = Uri.parse(profile.targetTreeUri)
        val root = safRepository.getTreeDocument(treeUri) ?: throw IllegalStateException("Target folder not accessible")
        val ignoreMatcher = IgnoreMatcher.fromProfile(profile, internalRoot)
        val targetIndex = SafTreeIndex.build(root, ignoreMatcher)
        val existing = fingerprintDao.getAllForProfile(profile.id).associateBy { it.relativePath }
        val addedPaths = mutableListOf<String>()
        val modifiedPaths = mutableListOf<String>()
        val deletedPaths = mutableListOf<String>()
        val entries = mutableListOf<FingerprintEntity>()
        for ((path, doc) in targetIndex.files) {
            val fp = buildFingerprint(profile.id, path, doc)
            val prev = existing[path]
            val changed = when {
                prev == null -> true
                prev.sizeBytes != fp.sizeBytes -> true
                prev.modifiedTimeEpochMillis != null && fp.modifiedTimeEpochMillis != null &&
                    prev.modifiedTimeEpochMillis != fp.modifiedTimeEpochMillis -> true
                fp.modifiedTimeEpochMillis == null &&
                    (prev.sha256 == null || fp.sha256 == null || prev.sha256 != fp.sha256) -> true
                else -> false
            }
            if (changed) {
                copyDocumentToFile(doc, File(internalRoot, path))
                if (prev == null) addedPaths.add(path) else modifiedPaths.add(path)
            }
            entries.add(fp)
        }
        if (profile.propagateDeletes) {
            val removed = existing.keys - targetIndex.files.keys
            for (path in removed) {
                val target = File(internalRoot, path)
                if (target.exists()) target.delete()
                deletedPaths.add(path)
            }
        }
        fingerprintDao.deleteForProfile(profile.id)
        fingerprintDao.upsertAll(entries)
        logWriter.log("Mirror target → internal: +${addedPaths.size}, ~${modifiedPaths.size}, -${deletedPaths.size}")
        return MirrorDiff(added = addedPaths, modified = modifiedPaths, deleted = deletedPaths)
    }

    suspend fun mirrorInternalToTarget(profile: Profile, internalRoot: File, logWriter: SyncLogWriter): MirrorDiff {
        val treeUri = Uri.parse(profile.targetTreeUri)
        val root = safRepository.getTreeDocument(treeUri) ?: throw IllegalStateException("Target folder not accessible")
        val ignoreMatcher = IgnoreMatcher.fromProfile(profile, internalRoot)
        val targetIndex = SafTreeIndex.build(root, ignoreMatcher)
        val internalIndex = LocalTreeIndex.build(internalRoot, ignoreMatcher)
        val addedPaths = mutableListOf<String>()
        val modifiedPaths = mutableListOf<String>()
        val deletedPaths = mutableListOf<String>()
        for ((path, file) in internalIndex.files) {
            val doc = targetIndex.files[path]
            if (doc == null) {
                writeFileToTree(root, path, file)
                addedPaths.add(path)
            } else {
                val targetSize = doc.length()
                val targetMtime = doc.lastModified()
                val sizeDiff = targetSize != file.length()
                val mtimeDiff = targetMtime > 0 && file.lastModified() != targetMtime
                val hashDiff = targetMtime <= 0 && !sizeDiff && !hashEquals(doc, file)
                if (sizeDiff || mtimeDiff || hashDiff) {
                    writeFileToDocument(doc, file)
                    modifiedPaths.add(path)
                }
            }
        }
        if (profile.propagateDeletes) {
            val removed = targetIndex.files.keys - internalIndex.files.keys
            for (path in removed) {
                targetIndex.files[path]?.delete()
                deletedPaths.add(path)
            }
        }
        logWriter.log("Mirror internal → target: +${addedPaths.size}, ~${modifiedPaths.size}, -${deletedPaths.size}")
        return MirrorDiff(added = addedPaths, modified = modifiedPaths, deleted = deletedPaths)
    }

    private fun buildFingerprint(profileId: String, path: String, doc: androidx.documentfile.provider.DocumentFile): FingerprintEntity {
        val size = max(0L, doc.length())
        val mtime = doc.lastModified().takeIf { it > 0 }
        val sha256 = if (mtime == null) sha256Document(doc) else null
        return FingerprintEntity(
            profileId = profileId,
            relativePath = path,
            sizeBytes = size,
            modifiedTimeEpochMillis = mtime,
            sha256 = sha256
        )
    }

    private fun copyDocumentToFile(doc: androidx.documentfile.provider.DocumentFile, dest: File) {
        dest.parentFile?.mkdirs()
        val temp = File(dest.parentFile, dest.name + ".reposync_tmp")
        safRepository.openInput(doc.uri)?.use { input ->
            temp.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        val expected = doc.length()
        if (expected > 0 && temp.length() != expected) {
            temp.delete()
            throw IllegalStateException("Write verification failed for ${dest.name}")
        }
        if (dest.exists()) dest.delete()
        if (!temp.renameTo(dest)) {
            temp.copyTo(dest, overwrite = true)
            temp.delete()
        }
        dest.setLastModified(doc.lastModified())
    }

    private fun writeFileToTree(root: androidx.documentfile.provider.DocumentFile, path: String, file: File) {
        val segments = path.split("/")
        var current = root
        segments.dropLast(1).forEach { segment ->
            val next = current.findFile(segment) ?: current.createDirectory(segment)
            if (next != null) current = next
        }
        writeFileToDirectory(current, segments.last(), file)
    }

    private fun writeFileToDocument(doc: androidx.documentfile.provider.DocumentFile, file: File) {
        safRepository.openOutput(doc.uri)?.use { output ->
            file.inputStream().use { input ->
                input.copyTo(output)
            }
        }
    }

    private fun writeFileToDirectory(
        dir: androidx.documentfile.provider.DocumentFile,
        name: String,
        file: File
    ) {
        val tempName = ".reposync_tmp_${System.currentTimeMillis()}_$name"
        val tempDoc = dir.createFile("application/octet-stream", tempName) ?: return
        writeFileToDocument(tempDoc, file)
        val expected = file.length()
        if (expected > 0 && tempDoc.length() != expected) {
            tempDoc.delete()
            throw IllegalStateException("Write verification failed for $name")
        }
        dir.findFile(name)?.delete()
        if (!tempDoc.renameTo(name)) {
            val target = dir.findFile(name) ?: dir.createFile("application/octet-stream", name)
            if (target != null) {
                writeFileToDocument(target, file)
            }
            tempDoc.delete()
        }
    }

    private fun hashEquals(
        doc: androidx.documentfile.provider.DocumentFile,
        file: File
    ): Boolean {
        val docHash = sha256Document(doc) ?: return false
        val fileHash = sha256File(file)
        return docHash == fileHash
    }

    private fun sha256Document(doc: androidx.documentfile.provider.DocumentFile): String? {
        return safRepository.openInput(doc.uri)?.use { input ->
            sha256FromStream(input)
        }
    }

    private fun sha256File(file: File): String {
        return file.inputStream().use { input ->
            sha256FromStream(input)
        }
    }

    private fun sha256FromStream(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        var read = input.read(buffer)
        while (read > 0) {
            digest.update(buffer, 0, read)
            read = input.read(buffer)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

class IgnoreMatcher private constructor(private val rules: List<org.eclipse.jgit.ignore.FastIgnoreRule>) {
    fun isIgnored(path: String, isDir: Boolean): Boolean = rules.any { it.isMatch(path, isDir) }

    companion object {
        fun fromProfile(profile: Profile, internalRoot: File): IgnoreMatcher {
            val patterns = mutableListOf<String>()
            patterns.addAll(DEFAULT_PATTERNS)
            patterns.addAll(profile.ignoreRules.patterns)
            val gitignore = File(internalRoot, ".gitignore")
            if (gitignore.exists()) {
                patterns.addAll(gitignore.readLines())
            }
            val rules = patterns.filter { it.isNotBlank() }.map { org.eclipse.jgit.ignore.FastIgnoreRule(it) }
            return IgnoreMatcher(rules)
        }

        fun defaultPatterns(): List<String> = DEFAULT_PATTERNS

        private val DEFAULT_PATTERNS = listOf(
            "*.tmp",
            "*.swp",
            ".DS_Store",
            "Thumbs.db",
            ".Trash",
            "~$*"
        )
    }
}

class SafTreeIndex private constructor(
    val files: Map<String, androidx.documentfile.provider.DocumentFile>
) {
    companion object {
        fun build(root: androidx.documentfile.provider.DocumentFile, ignoreMatcher: IgnoreMatcher): SafTreeIndex {
            val files = mutableMapOf<String, androidx.documentfile.provider.DocumentFile>()
            fun walk(current: androidx.documentfile.provider.DocumentFile, prefix: String) {
                current.listFiles().forEach { child ->
                    val relative = if (prefix.isEmpty()) child.name ?: "" else "$prefix/${child.name}"
                    if (relative.isBlank()) return@forEach
                    val isDir = child.isDirectory
                    if (ignoreMatcher.isIgnored(relative, isDir)) return@forEach
                    if (isDir) {
                        walk(child, relative)
                    } else {
                        files[relative] = child
                    }
                }
            }
            walk(root, "")
            return SafTreeIndex(files)
        }
    }
}

class LocalTreeIndex private constructor(
    val files: Map<String, File>
) {
    companion object {
        fun build(root: File, ignoreMatcher: IgnoreMatcher): LocalTreeIndex {
            val files = mutableMapOf<String, File>()
            fun walk(current: File, prefix: String) {
                current.listFiles()?.forEach { child ->
                    if (child.name == ".git") return@forEach
                    val relative = if (prefix.isEmpty()) child.name else "$prefix/${child.name}"
                    val isDir = child.isDirectory
                    if (ignoreMatcher.isIgnored(relative, isDir)) return@forEach
                    if (isDir) {
                        walk(child, relative)
                    } else {
                        files[relative] = child
                    }
                }
            }
            walk(root, "")
            return LocalTreeIndex(files)
        }
    }
}

class SyncLogWriter(
    private val context: Context,
    private val profile: Profile,
    private val cryptoStore: CryptoStore,
    private val onStep: ((String) -> Unit)? = null
) {
    private val lines = mutableListOf<String>()
    private val logFile = File(context.filesDir, "sync_logs/${profile.id}-${System.currentTimeMillis()}.log")
    var summary: SyncSummary = SyncSummary(0, 0, 0, 0)
        private set

    var currentStep: String = ""
        private set

    fun log(message: String) {
        val redacted = SecretRedactor.redact(message, secrets())
        lines.add(redacted)
    }

    fun step(step: String) {
        currentStep = step
        onStep?.invoke(step)
        log("Step: $step")
    }

    fun setSummary(summary: SyncSummary) {
        this.summary = summary
    }

    fun closeAndGetPath(): String? {
        logFile.parentFile?.mkdirs()
        logFile.writeText(lines.joinToString("\n"))
        return logFile.absolutePath
    }

    private fun secrets(): List<String> {
        val token = cryptoStore.getSecret("token_${profile.id}")
        val privateKey = cryptoStore.getSecret("ssh_priv_${profile.id}")
        val publicKey = cryptoStore.getSecret("ssh_pub_${profile.id}")
        val githubToken = cryptoStore.getSecret(GitHubAuthKeys.ACCESS_TOKEN)
        return listOfNotNull(token, privateKey, publicKey, githubToken, profile.remoteUrl)
    }
}

object SecretRedactor {
    fun redact(message: String, secrets: List<String>): String {
        var result = message
        secrets.filter { it.isNotBlank() }.forEach { secret ->
            result = result.replace(secret, "[REDACTED]", ignoreCase = true)
        }
        return result
    }
}
