package com.mykart.payment.metric

import com.mykart.payment.enums.Channel
import com.mykart.payment.repository.PayoutRepository
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Component

@Component
class PayoutMetrics(
    private val meterRegistry: MeterRegistry,
    private val payoutRepository: PayoutRepository
) {

    inner class Stopwatch(private val sample: Timer.Sample, private val timer: Timer) {
        fun stop() = sample.stop(timer)
    }

    private lateinit var createdCounter: Counter
    private lateinit var successCounter: Counter
    private lateinit var failedCounter: Counter
    private lateinit var retryCounter: Counter

    private val channelTimers = mutableMapOf<Channel, Timer>()

    @PostConstruct
    fun init() {
        createdCounter = Counter.builder("payout.created.total")
            .description("Total payouts created")
            .register(meterRegistry)

        successCounter = Counter.builder("payout.success.total")
            .description("Total payouts successfully disbursed")
            .register(meterRegistry)

        failedCounter = Counter.builder("payout.failed.total")
            .description("Total payouts that failed")
            .register(meterRegistry)

        retryCounter = Counter.builder("payout.retry.total")
            .description("Total payout retry attempts")
            .register(meterRegistry)

        Channel.entries.forEach { channel ->
            channelTimers[channel] = Timer.builder("payout.channel.latency")
                .tag("channel", channel.name)
                .description("Latency of payment channel transfer calls")
                .register(meterRegistry)
        }

        meterRegistry.gauge("payout.pending.count", payoutRepository) { repo ->
            repo.countByStatus(com.mykart.payment.enums.PayoutStatus.PENDING).toDouble()
        }

        meterRegistry.gauge("payout.retryable.count", payoutRepository) { repo ->
            repo.countRetryable().toDouble()
        }
    }

    fun incrementCreated() = createdCounter.increment()

    fun incrementSuccess(channel: Channel) {
        successCounter.increment()
        Counter.builder("payout.success.by.channel")
            .tag("channel", channel.name)
            .register(meterRegistry)
            .increment()
    }

    fun incrementFailed(channel: Channel?) {
        failedCounter.increment()
        channel?.let {
            Counter.builder("payout.failed.by.channel")
                .tag("channel", it.name)
                .register(meterRegistry)
                .increment()
        }
    }

    fun incrementRetry() = retryCounter.increment()

    fun startChannelTimer(channel: Channel): Stopwatch {
        val timer = channelTimers.getValue(channel)
        return Stopwatch(Timer.start(meterRegistry), timer)
    }
}
