-- Compatibility callback for clean installations and modern-only schemas.
--
-- Historical migrations V2026072403 and V2026072501 read five legacy tables.
-- Existing legacy databases already own the full tables, so every statement
-- below is a no-op there. On a clean installation these tables stay empty and
-- make the historical backfills no-ops without changing historical checksums.

CREATE TABLE IF NOT EXISTS `chezhu` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `addtime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `zhanghao` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '账号',
  `mima` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '密码',
  `xingming` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '姓名',
  `xingbie` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '性别',
  `shouji` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '手机',
  `touxiang` longtext COLLATE utf8mb4_unicode_ci COMMENT '头像',
  PRIMARY KEY (`id`),
  UNIQUE KEY `zhanghao` (`zhanghao`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='历史会员迁移兼容表；全新安装保持为空';

CREATE TABLE IF NOT EXISTS `fuwuyuyue` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `addtime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `yuyuebianhao` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '预约编号',
  `fuwumingcheng` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '服务名称',
  `fuwufenlei` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '服务分类',
  `fengmian` longtext COLLATE utf8mb4_unicode_ci COMMENT '封面',
  `jiage` double DEFAULT NULL COMMENT '价格',
  `weixiuzhuangtai` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '维修状态',
  `yuyueshijian` datetime DEFAULT NULL COMMENT '预约时间',
  `cheliangwenti` longtext COLLATE utf8mb4_unicode_ci COMMENT '车辆问题',
  `zhanghao` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '账号',
  `xingming` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '姓名',
  `shouji` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '手机',
  `chepaihao` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '车牌号',
  `weixiuzhanghao` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '维修账号',
  `weixiuxingming` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '维修姓名',
  `sfsh` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT '待审核' COMMENT '是否审核',
  `shhf` longtext COLLATE utf8mb4_unicode_ci COMMENT '审核回复',
  PRIMARY KEY (`id`),
  UNIQUE KEY `yuyuebianhao` (`yuyuebianhao`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='历史预约迁移兼容表；全新安装保持为空';

CREATE TABLE IF NOT EXISTS `peijianxinxi` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `addtime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `peijianbianhao` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '配件编号',
  `peijianmingcheng` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '配件名称',
  `peijianzhonglei` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '配件种类',
  `pinpai` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '品牌',
  `shuliang` int(11) NOT NULL COMMENT '数量',
  `shoujia` double DEFAULT NULL COMMENT '售价',
  `tupian` longtext COLLATE utf8mb4_unicode_ci COMMENT '图片',
  `dengjiriqi` date DEFAULT NULL COMMENT '登记日期',
  `peijianjieshao` longtext COLLATE utf8mb4_unicode_ci COMMENT '配件介绍',
  PRIMARY KEY (`id`),
  UNIQUE KEY `peijianbianhao` (`peijianbianhao`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='历史库存迁移兼容表；全新安装保持为空';

CREATE TABLE IF NOT EXISTS `weixiujilu` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `addtime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `weixiubianhao` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '维修编号',
  `fuwumingcheng` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '服务名称',
  `fuwufenlei` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '服务分类',
  `fengmian` longtext COLLATE utf8mb4_unicode_ci COMMENT '封面',
  `jiage` double DEFAULT NULL COMMENT '价格',
  `peijianmingcheng` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '配件名称',
  `allshoujia` double DEFAULT NULL COMMENT '配件售价',
  `zongjia` double DEFAULT NULL COMMENT '总价',
  `weixiushijian` datetime DEFAULT NULL COMMENT '维修时间',
  `weixiushuoming` longtext COLLATE utf8mb4_unicode_ci COMMENT '维修说明',
  `zhanghao` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '账号',
  `xingming` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '姓名',
  `shouji` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '手机',
  `chepaihao` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '车牌号',
  `weixiuzhanghao` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '维修账号',
  `weixiuxingming` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '维修姓名',
  `ispay` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT '未支付' COMMENT '是否支付',
  PRIMARY KEY (`id`),
  UNIQUE KEY `weixiubianhao` (`weixiubianhao`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='历史服务记录迁移兼容表；全新安装保持为空';

CREATE TABLE IF NOT EXISTS `peijianchuku` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `addtime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `chukubianhao` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '出库编号',
  `peijianmingcheng` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '配件名称',
  `peijianzhonglei` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '配件种类',
  `shoujia` double DEFAULT NULL COMMENT '售价',
  `shuliang` int(11) NOT NULL COMMENT '数量',
  `zongjia` double DEFAULT NULL COMMENT '总价',
  `kehu` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '客户',
  `chukushijian` date DEFAULT NULL COMMENT '出库时间',
  `beizhu` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `weixiuzhanghao` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '维修账号',
  `weixiuxingming` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '维修姓名',
  PRIMARY KEY (`id`),
  UNIQUE KEY `chukubianhao` (`chukubianhao`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='历史库存出库迁移兼容表；全新安装保持为空';
