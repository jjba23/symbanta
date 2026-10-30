/* symbanta
*
* SPDX-License-Identifier: MIT
* Copyright © Josep Bigorra <jjbigorra@gmail.com>
*/

package org.jointhefreeworld.symbanta.config

import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

/** Background work: currently the analytics rollup refresh (see [StatsRollupTrigger]). */
@Configuration
@EnableScheduling
class SchedulingConfig
