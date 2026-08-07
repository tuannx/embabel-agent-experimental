/*
 * Copyright 2024-2026 Embabel Pty Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.embabel.agent.dogfood.host.improve

import com.embabel.agent.api.common.AiBuilder
import com.embabel.agent.dogfood.DogfoodApplication
import com.embabel.agent.dogfood.config.DogfoodProperties
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.ConfigurableApplicationContext

object CodeImproveSpringHost {
    fun withContext(block: (ConfigurableApplicationContext) -> Int): Int {
        val ctx = SpringApplicationBuilder(DogfoodApplication::class.java)
            .web(WebApplicationType.NONE)
            .properties(
                "dogfood.run-on-startup=false",
                "spring.main.banner-mode=off",
            )
            .run("--logging.level.root=WARN")
        return try {
            block(ctx)
        } finally {
            ctx.close()
        }
    }

    fun agentCodeBackend(ctx: ConfigurableApplicationContext): AgentCodeImproveBackend =
        AgentCodeImproveBackend(
            aiBuilder = ctx.getBean(AiBuilder::class.java),
            properties = ctx.getBean(DogfoodProperties::class.java),
        )
}
