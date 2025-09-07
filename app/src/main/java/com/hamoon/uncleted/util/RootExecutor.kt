// ================================================================================
// ### FILE: app/src/main/java/com/hamoon/uncleted/util/RootExecutor.kt
// ================================================================================
package com.hamoon.uncleted.util

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

object RootExecutor {

    private const val TAG = "RootExecutor"
    private const val COMMAND_TIMEOUT_SECONDS = 10L

    data class CommandResult(
        val output: List<String>,
        val errorOutput: List<String>,
        val exitCode: Int
    ) {
        val isSuccess: Boolean
            get() = exitCode == 0

        val isRootAvailable: Boolean
            get() = exitCode != -1
    }

    suspend fun run(command: String): CommandResult = withContext(Dispatchers.IO) {
        var process: Process? = null
        return@withContext try {
            Log.d(TAG, "Executing root command: '$command'")

            process = ProcessBuilder("su").start()

            DataOutputStream(process.outputStream).use { os ->
                os.writeBytes("$command\n")
                os.flush()
                os.writeBytes("exit\n")
                os.flush()
            }

            val processCompleted = process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)

            val result = if (!processCompleted) {
                Log.d(TAG, "Command timed out: '$command'")
                process.destroyForcibly()
                CommandResult(emptyList(), listOf("Command timed out"), -1)
            } else {
                val output = try {
                    process.inputStream.bufferedReader().readLines()
                } catch (e: Exception) {
                    Log.d(TAG, "Failed to read output stream: ${e.message}")
                    emptyList<String>()
                }

                val errorOutput = try {
                    process.errorStream.bufferedReader().readLines()
                } catch (e: Exception) {
                    Log.d(TAG, "Failed to read error stream: ${e.message}")
                    emptyList<String>()
                }

                val exitCode = process.exitValue()

                Log.d(TAG, "Command completed: '$command' | Exit Code: $exitCode")
                if (output.isNotEmpty()) {
                    Log.d(TAG, "Output: ${output.joinToString("\\n")}")
                }
                if (errorOutput.isNotEmpty()) {
                    Log.d(TAG, "Error Output: ${errorOutput.joinToString("\\n")}")
                }

                CommandResult(output, errorOutput, exitCode)
            }

            result
        } catch (e: IOException) {
            Log.d(TAG, "Root command execution failed (expected on non-rooted device): '$command' - ${e.message}")
            CommandResult(emptyList(), listOf("Root not available: ${e.message}"), -1)
        } catch (e: SecurityException) {
            Log.d(TAG, "Security exception during root command execution: '$command' - ${e.message}")
            CommandResult(emptyList(), listOf("Security exception: ${e.message}"), -1)
        } catch (e: Exception) {
            Log.w(TAG, "Unexpected exception during root command execution: '$command'", e)
            CommandResult(emptyList(), listOf("Unexpected error: ${e.message}"), -1)
        } finally {
            process?.let { proc ->
                try {
                    if (proc.isAlive) {
                        proc.destroyForcibly()
                    } else {
                        // This empty else branch is required by the compiler because the if-statement
                        // is the last statement in the try-catch, which is treated as an expression.
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Failed to destroy process: ${e.message}")
                }
            }
        }
    }

    suspend fun runMultiple(commands: List<String>): List<CommandResult> {
        return commands.map { command ->
            run(command)
        }
    }

    suspend fun isRootAvailable(): Boolean {
        return try {
            val result = run("echo test")
            result.isRootAvailable && result.isSuccess
        } catch (e: Exception) {
            Log.d(TAG, "Root availability check failed: ${e.message}")
            false
        }
    }
}