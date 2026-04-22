package com.seedcrm.crm.planorder.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seedcrm.crm.planorder.entity.OrderRoleRecord;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface OrderRoleRecordMapper extends BaseMapper<OrderRoleRecord> {

    @Select("""
            SELECT COUNT(DISTINCT plan_order_id)
            FROM order_role_record
            WHERE user_id = #{userId}
            """)
    Long countDistinctPlanOrdersByUserId(@Param("userId") Long userId);

    @Select("""
            SELECT role_code AS roleCode, COUNT(1) AS roleCount
            FROM order_role_record
            WHERE user_id = #{userId}
            GROUP BY role_code
            ORDER BY role_code
            """)
    List<Map<String, Object>> selectRoleDistributionByUserId(@Param("userId") Long userId);

    @Select("""
            SELECT COUNT(DISTINCT rr.plan_order_id)
            FROM order_role_record rr
            INNER JOIN plan_order po ON po.id = rr.plan_order_id
            WHERE rr.user_id = #{userId}
              AND po.status = 'FINISHED'
            """)
    Long countFinishedServicesByUserId(@Param("userId") Long userId);
}
