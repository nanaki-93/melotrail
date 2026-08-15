package app.melotrail.errors

class AppErrorException(val error: AppError) : RuntimeException(error.userMessage)
