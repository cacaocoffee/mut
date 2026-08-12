package kr.kcocktail.common.revalidate

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.TaskExecutor
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

@Configuration
@EnableConfigurationProperties(RevalidateProperties::class)
class RevalidateConfig {

    /**
     * 전용 스레드 풀을 둔다. 공용 `applicationTaskExecutor` 를 쓰지 않는 이유가 둘이다.
     *
     * 하나, 배치(SPEC-05 §8)와 풀을 나눠 쓰면 **긴 배치가 재생성을 굶긴다.**
     * 둘, 스타터가 여러 `TaskExecutor` 를 올릴 때 어느 것이 주입될지 이름으로 못 박아 둔다.
     *
     * 큐가 차면 호출 스레드가 직접 보낸다 — 버리지 않는다. 재생성을 놓치면
     * 프론트가 옛 내용을 계속 보여 주고, 그것을 알아챌 방법이 없다.
     * 여기서 잠깐 막히는 편이 낫다 (이미 커밋 뒤라 발행은 끝났다).
     */
    @Bean
    fun revalidateTaskExecutor(): TaskExecutor = ThreadPoolTaskExecutor().apply {
        corePoolSize = 1
        maxPoolSize = 2
        queueCapacity = 100
        setThreadNamePrefix("revalidate-")
        setRejectedExecutionHandler { runnable, _ -> runnable.run() }
        initialize()
    }
}
