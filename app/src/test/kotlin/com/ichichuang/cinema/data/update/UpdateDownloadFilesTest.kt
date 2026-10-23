package com.ichichuang.cinema.data.update

import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UpdateDownloadFilesTest {
    @get:Rule val temp = TemporaryFolder()
    private fun target() = File(temp.root, "candidate.apk")
    private fun partial() = File(temp.root, "candidate.part")

    @Test fun completeTransferIsPromotedAndPartialIsRemoved() {
        val result = UpdateDownloadFiles.write(target(), 4) { it.writeText("data") }
        assertEquals("data", result.readText())
        assertFalse(partial().exists())
    }

    @Test fun interruptedTransferRemovesPartial() {
        assertThrows(IOException::class.java) {
            UpdateDownloadFiles.write(target(), 4) { it.writeText("da"); throw IOException("synthetic interruption") }
        }
        assertFalse(partial().exists())
        assertFalse(target().exists())
    }

    @Test fun cancellationRemovesPartialAndPropagates() {
        assertThrows(CancellationException::class.java) {
            UpdateDownloadFiles.write(target(), 4) { it.writeText("da"); throw CancellationException("cancelled") }
        }
        assertFalse(partial().exists())
        assertFalse(target().exists())
    }

    @Test fun sizeMismatchRemovesPartial() {
        assertThrows(Exception::class.java) { UpdateDownloadFiles.write(target(), 5) { it.writeText("data") } }
        assertFalse(partial().exists())
        assertFalse(target().exists())
    }

    @Test fun failedRenameRemovesPartial() {
        target().mkdir()
        File(target(), "occupied").writeText("keep")
        assertThrows(Exception::class.java) { UpdateDownloadFiles.write(target(), 4) { it.writeText("data") } }
        assertFalse(partial().exists())
        assertEquals("keep", File(target(), "occupied").readText())
    }

    @Test fun oldPartialCannotContaminateNewTransfer() {
        partial().writeText("stale")
        UpdateDownloadFiles.write(target(), 4) { assertFalse(it.exists()); it.writeText("data") }
        assertEquals("data", target().readText())
        assertFalse(partial().exists())
    }
}
