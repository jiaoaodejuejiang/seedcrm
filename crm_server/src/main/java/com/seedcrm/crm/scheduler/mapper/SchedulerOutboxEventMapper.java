package com.seedcrm.crm.scheduler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seedcrm.crm.scheduler.entity.SchedulerOutboxEvent;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SchedulerOutboxEventMapper extends BaseMapper<SchedulerOutboxEvent> {
}
