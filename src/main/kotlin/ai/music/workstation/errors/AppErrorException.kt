package ai.music.workstation.errors

class AppErrorException(val error: AppError) : RuntimeException(error.userMessage)
