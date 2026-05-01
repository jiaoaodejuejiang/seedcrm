package com.seedcrm.crm.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seedcrm.crm.order.entity.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface OrderMapper extends BaseMapper<Order> {

    @Select("""
            SELECT id, order_no, clue_id, customer_id, source_channel, source, source_id,
                   external_partner_code, external_order_id, external_trade_no, external_member_id,
                   external_promoter_id, external_status, refund_status, raw_data,
                   type, amount, deposit, status,
                   appointment_time, appointment_store_name, arrive_time, complete_time, remark, service_detail_json, verification_status,
                   verification_method, verification_code, verification_time, verification_operator_id,
                   create_time, update_time
            FROM order_info
            WHERE id = #{id}
            FOR UPDATE
            """)
    Order selectByIdForUpdate(@Param("id") Long id);
}
