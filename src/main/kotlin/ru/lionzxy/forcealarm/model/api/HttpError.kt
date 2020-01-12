package ru.lionzxy.forcealarm.model.api;

enum class HttpError(val id: String, val reason: String = "") {
    UNKNOWN("unknown"),
    EMPTY("empty"),
    INTERNAL("internal"),
    NOTFOUND("notfound", "Нету такого пути :("),
    NOTFOUNDEVENT("notfoundevent", "Нет такого события под таким id"),
    NOTLOADEDYET("notloadedyet", "Данные еще загружаются... Обратитесь позже"),
    BADPARAMS("badparams", "Этот метод надо использовать не так и передавать надо другие данные"),
    BADFORM("badform", "Этот метод надо использовать не так и передавать надо другие данные"),
    AUTHERROR("autherror", "Пользователь не авторизован. Пройдите регистрацию, пожалуйста"),
    EXPIRED("expired", "Уже прошла жеребьевка. Теперь вы не можете это сделать :(");

    override fun toString() = id
}

data class ErrorResponse(val status: HttpError, val reason: Any = status.reason)
