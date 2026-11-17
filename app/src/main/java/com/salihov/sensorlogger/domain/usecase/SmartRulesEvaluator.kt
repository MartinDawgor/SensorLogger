package com.salihov.sensorlogger.domain.usecase

import com.salihov.sensorlogger.domain.model.RuleActionType
import com.salihov.sensorlogger.domain.model.RuleConditionType
import com.salihov.sensorlogger.domain.model.SmartRule
import java.util.concurrent.ConcurrentHashMap

class SmartRulesEvaluator(
    private val clockMillis: () -> Long
) {
    private val lastTriggerMillis = ConcurrentHashMap<String, Long>()

    fun evaluate(
        rules: List<SmartRule>,
        condition: RuleConditionType,
        currentValue: Float
    ): List<RuleActionType> {
        val now = clockMillis()
        val triggered = mutableListOf<RuleActionType>()
        for (rule in rules) {
            if (!rule.enabled || rule.condition != condition) continue
            val matched = when (condition) {
                RuleConditionType.G_FORCE_ABOVE -> currentValue >= rule.threshold
                RuleConditionType.LIGHT_BELOW -> currentValue <= rule.threshold
                RuleConditionType.SOUND_ABOVE -> currentValue >= rule.threshold
                RuleConditionType.AUTO_STOP_SECONDS -> currentValue >= rule.threshold
            }
            if (!matched) continue
            val prev = lastTriggerMillis[rule.id] ?: 0L
            if (now - prev >= rule.cooldownSec * 1000L) {
                lastTriggerMillis[rule.id] = now
                triggered += rule.action
            }
        }
        return triggered
    }

    fun resetCooldowns() {
        lastTriggerMillis.clear()
    }
}
