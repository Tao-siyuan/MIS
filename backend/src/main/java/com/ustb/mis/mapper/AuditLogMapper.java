package com.ustb.mis.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ustb.mis.entity.AuditLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {
}
