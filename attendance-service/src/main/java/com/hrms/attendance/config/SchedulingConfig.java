package com.hrms.attendance.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Bật @Scheduled cho attendance (job sinh roster định kỳ). */
@Configuration
@EnableScheduling
public class SchedulingConfig {}
