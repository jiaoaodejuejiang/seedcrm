package com.seedcrm.crm.risk.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seedcrm.crm.risk.entity.IdempotentRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface IdempotentRecordMapper extends BaseMapper<IdempotentRecord> {

    @Select("""
            SELECT id, biz_key, biz_type, status, create_time
            FROM idempotent_record
            WHERE biz_key = #{bizKey}
            LIMIT 1
            """)
    IdempotentRecord selectByBizKey(@Param("bizKey") String bizKey);
}
