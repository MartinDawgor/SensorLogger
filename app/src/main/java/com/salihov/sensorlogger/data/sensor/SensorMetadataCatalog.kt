package com.salihov.sensorlogger.data.sensor

import com.salihov.sensorlogger.domain.model.SensorCategory

data class SensorMetadata(
    val friendlyName: String,
    val category: SensorCategory,
    val description: String,
    val unit: String,
    val axisLabels: List<String>
)

object SensorMetadataCatalog {

    private val byType: Map<Int, SensorMetadata> = mapOf(
        1 to SensorMetadata(
            friendlyName = "Акселерометр",
            category = SensorCategory.MOTION,
            description = "Измеряет линейное ускорение по 3 осям с учётом силы тяжести. Используется для наклона, тряски и шагов.",
            unit = "м/с²",
            axisLabels = listOf("X", "Y", "Z")
        ),
        2 to SensorMetadata(
            friendlyName = "Магнитометр (Компас)",
            category = SensorCategory.POSITION,
            description = "Измеряет напряжённость магнитного поля вокруг телефона по 3 осям.",
            unit = "мкТл",
            axisLabels = listOf("X", "Y", "Z")
        ),
        3 to SensorMetadata(
            friendlyName = "Ориентация (устар.)",
            category = SensorCategory.POSITION,
            description = "Углы поворота устройства относительно сторон света и горизонта.",
            unit = "°",
            axisLabels = listOf("Азимут", "Тангаж", "Крен")
        ),
        4 to SensorMetadata(
            friendlyName = "Гироскоп",
            category = SensorCategory.MOTION,
            description = "Измеряет угловую скорость вращения устройства вокруг каждой из 3 осей.",
            unit = "рад/с",
            axisLabels = listOf("X", "Y", "Z")
        ),
        5 to SensorMetadata(
            friendlyName = "Датчик освещённости",
            category = SensorCategory.ENVIRONMENT,
            description = "Измеряет уровень окружающего света в люксах. Используется для автояркости экрана.",
            unit = "лк",
            axisLabels = listOf("Освещённость")
        ),
        6 to SensorMetadata(
            friendlyName = "Барометр (Давление)",
            category = SensorCategory.ENVIRONMENT,
            description = "Измеряет атмосферное давление. Позволяет оценивать высоту над уровнем моря и погоду.",
            unit = "гПа",
            axisLabels = listOf("Давление")
        ),
        7 to SensorMetadata(
            friendlyName = "Термометр устройства",
            category = SensorCategory.ENVIRONMENT,
            description = "Датчик температуры устройства (устаревший тип).",
            unit = "°C",
            axisLabels = listOf("Температура")
        ),
        8 to SensorMetadata(
            friendlyName = "Датчик приближения",
            category = SensorCategory.POSITION,
            description = "Определяет, поднесён ли объект (например, ухо при звонке) близко к экрану.",
            unit = "см",
            axisLabels = listOf("Дистанция")
        ),
        9 to SensorMetadata(
            friendlyName = "Датчик гравитации",
            category = SensorCategory.MOTION,
            description = "Выделяет только вектор силы тяжести (без ускорения движения телефона).",
            unit = "м/с²",
            axisLabels = listOf("X", "Y", "Z")
        ),
        10 to SensorMetadata(
            friendlyName = "Линейное ускорение",
            category = SensorCategory.MOTION,
            description = "Показывает чистое ускорение самого устройства без влияния гравитации.",
            unit = "м/с²",
            axisLabels = listOf("X", "Y", "Z")
        ),
        11 to SensorMetadata(
            friendlyName = "Вектор вращения",
            category = SensorCategory.POSITION,
            description = "Точная ориентация телефона в пространстве в виде кватерниона (акселерометр + гироскоп + компас).",
            unit = "",
            axisLabels = listOf("X·sin", "Y·sin", "Z·sin", "cos", "Точность")
        ),
        12 to SensorMetadata(
            friendlyName = "Датчик влажности",
            category = SensorCategory.ENVIRONMENT,
            description = "Измеряет относительную влажность окружающего воздуха.",
            unit = "%",
            axisLabels = listOf("Влажность")
        ),
        13 to SensorMetadata(
            friendlyName = "Термометр среды",
            category = SensorCategory.ENVIRONMENT,
            description = "Измеряет температуру воздуха вокруг устройства.",
            unit = "°C",
            axisLabels = listOf("Температура")
        ),
        14 to SensorMetadata(
            friendlyName = "Магнитометр (без калибровки)",
            category = SensorCategory.POSITION,
            description = "Сырые показания магнитного поля и смещения жёсткого железа.",
            unit = "мкТл",
            axisLabels = listOf("X", "Y", "Z", "ΔX", "ΔY", "ΔZ")
        ),
        15 to SensorMetadata(
            friendlyName = "Игровой вектор вращения",
            category = SensorCategory.POSITION,
            description = "Ориентация в пространстве без привязки к магнитному северу (без компаса).",
            unit = "",
            axisLabels = listOf("X·sin", "Y·sin", "Z·sin", "cos")
        ),
        16 to SensorMetadata(
            friendlyName = "Гироскоп (без калибровки)",
            category = SensorCategory.MOTION,
            description = "Сырая угловая скорость без системной компенсации дрейфа нуля.",
            unit = "рад/с",
            axisLabels = listOf("X", "Y", "Z", "Дрейф X", "Дрейф Y", "Дрейф Z")
        ),
        17 to SensorMetadata(
            friendlyName = "Детектор значимого движения",
            category = SensorCategory.MOTION,
            description = "Триггерный датчик: однократно срабатывает при заметном перемещении пользователя.",
            unit = "",
            axisLabels = listOf("Событие")
        ),
        18 to SensorMetadata(
            friendlyName = "Детектор шага",
            category = SensorCategory.MOTION,
            description = "Выдаёт импульс 1.0 при каждом зафиксированном шаге пользователя.",
            unit = "шаг",
            axisLabels = listOf("Импульс")
        ),
        19 to SensorMetadata(
            friendlyName = "Шагомер (Счётчик шагов)",
            category = SensorCategory.MOTION,
            description = "Накапливает общее число шагов с момента последней перезагрузки устройства.",
            unit = "шаги",
            axisLabels = listOf("Всего шагов")
        ),
        20 to SensorMetadata(
            friendlyName = "Геомагнитный вектор вращения",
            category = SensorCategory.POSITION,
            description = "Ориентация по акселерометру и магнитометру без гироскопа (экономичный режим).",
            unit = "",
            axisLabels = listOf("X·sin", "Y·sin", "Z·sin", "cos", "Точность")
        ),
        21 to SensorMetadata(
            friendlyName = "Пульсометр",
            category = SensorCategory.BIOMETRIC,
            description = "Измеряет частоту сердечных сокращений (требует разрешение «Датчики тела»).",
            unit = "уд/мин",
            axisLabels = listOf("Пульс")
        ),
        22 to SensorMetadata(
            friendlyName = "Детектор наклона",
            category = SensorCategory.POSITION,
            description = "Фиксирует поворот устройства на угол более ~35° (триггерный).",
            unit = "",
            axisLabels = listOf("Наклон")
        ),
        26 to SensorMetadata(
            friendlyName = "Жест поднятия к лицу",
            category = SensorCategory.POSITION,
            description = "Срабатывает при повороте устройства к лицу.",
            unit = "",
            axisLabels = listOf("Жест")
        ),
        27 to SensorMetadata(
            friendlyName = "Ориентация экрана",
            category = SensorCategory.POSITION,
            description = "Определяет текущую ориентацию корпуса (0°, 90°, 180°, 270°) для автоповорота.",
            unit = "",
            axisLabels = listOf("Положение")
        ),
        28 to SensorMetadata(
            friendlyName = "Поза 6DoF",
            category = SensorCategory.POSITION,
            description = "Положение и вращение устройства с 6 степенями свободы.",
            unit = "",
            axisLabels = listOf("X·sin", "Y·sin", "Z·sin", "cos", "Tx", "Ty")
        ),
        29 to SensorMetadata(
            friendlyName = "Детектор неподвижности",
            category = SensorCategory.MOTION,
            description = "Срабатывает, когда устройство неподвижно в течение заданного времени.",
            unit = "",
            axisLabels = listOf("Статус")
        ),
        30 to SensorMetadata(
            friendlyName = "Детектор начала движения",
            category = SensorCategory.MOTION,
            description = "Срабатывает при начале движения устройства после покоя.",
            unit = "",
            axisLabels = listOf("Статус")
        ),
        31 to SensorMetadata(
            friendlyName = "Сердечный импульс",
            category = SensorCategory.BIOMETRIC,
            description = "Фиксирует каждый отдельный удар сердца и уверенность измерения.",
            unit = "",
            axisLabels = listOf("Уверенность")
        ),
        34 to SensorMetadata(
            friendlyName = "Датчик ношения на теле",
            category = SensorCategory.BIOMETRIC,
            description = "Определяет, надето ли устройство (часы/браслет) на тело.",
            unit = "",
            axisLabels = listOf("На теле")
        ),
        35 to SensorMetadata(
            friendlyName = "Акселерометр (без калибровки)",
            category = SensorCategory.MOTION,
            description = "Сырое ускорение без вычета системного смещения калибровки.",
            unit = "м/с²",
            axisLabels = listOf("X", "Y", "Z", "ΔX", "ΔY", "ΔZ")
        ),
        36 to SensorMetadata(
            friendlyName = "Угол раскрытия шарнира",
            category = SensorCategory.POSITION,
            description = "Угол между половинками складного устройства.",
            unit = "°",
            axisLabels = listOf("Угол")
        ),
        37 to SensorMetadata(
            friendlyName = "Направленный гироскоп",
            category = SensorCategory.MOTION,
            description = "Датчик направления углового движения.",
            unit = "рад/с",
            axisLabels = listOf("X", "Y", "Z")
        )
    )

    fun resolve(type: Int, stringType: String, name: String, isWakeUp: Boolean = false): SensorMetadata {
        val base = byType[type] ?: resolveFallback(stringType, name)
        val finalName = if (isWakeUp && "wake-up" !in base.friendlyName.lowercase()) {
            "${base.friendlyName} (Wake-up)"
        } else {
            base.friendlyName
        }
        return base.copy(friendlyName = finalName)
    }

    private fun resolveFallback(stringType: String, name: String): SensorMetadata {
        val lowered = "$stringType $name".lowercase()
        return when {
            "aod" in lowered -> SensorMetadata(
                "Датчик Always-On Display (AOD)",
                SensorCategory.ENVIRONMENT,
                "Определяет условия для отображения постоянно активного экрана (освещённость/карман).",
                "",
                listOf("Состояние")
            )
            "pickup" in lowered -> SensorMetadata(
                "Датчик поднятия телефона",
                SensorCategory.MOTION,
                "Фиксирует жест поднятия смартфона со стола для пробуждения экрана.",
                "",
                listOf("Событие")
            )
            "large_area" in lowered || "touch sensor" in lowered -> SensorMetadata(
                "Датчик касания большой площади",
                SensorCategory.POSITION,
                "Фиксирует прикосновение большой площади к экрану (щека во время звонка или карман).",
                "",
                listOf("Статус")
            )
            "sar" in lowered -> SensorMetadata(
                "Датчик приближения к телу (SAR)",
                SensorCategory.POSITION,
                "Ёмкостный датчик обнаружения тела рядом с антеннами для снижения мощности излучения.",
                "",
                listOf("Состояние")
            )
            "accel" in lowered -> SensorMetadata(
                "Акселерометр (OEM)",
                SensorCategory.MOTION,
                "OEM-вариант акселерометра: измеряет ускорение устройства.",
                "м/с²",
                listOf("X", "Y", "Z")
            )
            "gyro" in lowered -> SensorMetadata(
                "Гироскоп (OEM)",
                SensorCategory.MOTION,
                "OEM-вариант гироскопа: измеряет угловую скорость вращения.",
                "рад/с",
                listOf("X", "Y", "Z")
            )
            "mag" in lowered -> SensorMetadata(
                "Магнитометр (OEM)",
                SensorCategory.POSITION,
                "OEM-вариант магнитометра: измеряет магнитное поле.",
                "мкТл",
                listOf("X", "Y", "Z")
            )
            "light" in lowered || "als" in lowered -> SensorMetadata(
                "Датчик освещённости (OEM)",
                SensorCategory.ENVIRONMENT,
                "Оптический датчик уровня освещённости.",
                "лк",
                listOf("Значение")
            )
            "prox" in lowered -> SensorMetadata(
                "Датчик приближения (OEM)",
                SensorCategory.POSITION,
                "Датчик приближения объекта к экрану.",
                "см",
                listOf("Дистанция")
            )
            "step" in lowered || "pedometer" in lowered -> SensorMetadata(
                "Шагомер (OEM)",
                SensorCategory.MOTION,
                "Датчик подсчёта шагов или активности.",
                "шаги",
                listOf("Значение")
            )
            "heart" in lowered || "ppg" in lowered -> SensorMetadata(
                "Пульсометр (OEM)",
                SensorCategory.BIOMETRIC,
                "Биометрический оптический датчик пульса.",
                "уд/мин",
                listOf("Значение")
            )
            "temp" in lowered -> SensorMetadata(
                "Датчик температуры (OEM)",
                SensorCategory.ENVIRONMENT,
                "Датчик температуры.",
                "°C",
                listOf("Температура")
            )
            "pressure" in lowered || "baro" in lowered -> SensorMetadata(
                "Барометр (OEM)",
                SensorCategory.ENVIRONMENT,
                "Датчик атмосферного давления.",
                "гПа",
                listOf("Давление")
            )
            else -> SensorMetadata(
                name.ifBlank { "Системный датчик" },
                SensorCategory.OTHER,
                "Производительский / виртуальный датчик ($stringType). Записывает сырые числовые каналы.",
                "",
                emptyList()
            )
        }
    }
}
