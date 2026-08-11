package ai.music.workstation.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ai.music.workstation.queue.Job
import ai.music.workstation.queue.JobStatus
import ai.music.workstation.queue.ProcessingQueue

class QueueViewModel(
    private val queue: ProcessingQueue
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _jobs = queue.jobs
    val jobs: StateFlow<List<Job>> = _jobs

    private val _isProcessing = queue.isProcessing
    val isProcessing: StateFlow<Boolean> = _isProcessing

    fun cancelJob(jobId: String) {
        scope.launch {
            queue.cancelJob(jobId)
        }
    }

    fun retryJob(jobId: String) {
        scope.launch {
            queue.retryJob(jobId)
        }
    }

    fun clearCompleted() {
        scope.launch {
            queue.clearCompleted()
        }
    }

    fun startProcessing() {
        queue.startProcessing()
    }

    fun stopProcessing() {
        scope.launch {
            queue.stopProcessing()
        }
    }
}
