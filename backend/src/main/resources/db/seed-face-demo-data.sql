SET NAMES utf8mb4;

-- FACE 演示数据使用固定 ID，脚本可重复执行。
DELETE FROM banner WHERE id BETWEEN 2607230101 AND 2607230105;
INSERT INTO banner (id, shop_id, title, image_url, target_type, target_value, sort_order, status, created_at) VALUES
(2607230101,1,'焕亮新生季','upload/banner-01.webp','URL','/pages/services/index',10,'ACTIVE',NOW(3)),
(2607230102,1,'深层补水护理','upload/banner-02.webp','URL','/pages/services/index',20,'ACTIVE',NOW(3)),
(2607230103,1,'舒缓修护计划','upload/banner-03.webp','URL','/pages/services/index',30,'ACTIVE',NOW(3)),
(2607230104,1,'专属肌肤管理','upload/banner-04.webp','URL','/pages/staff/index',40,'ACTIVE',NOW(3)),
(2607230105,1,'会员臻享礼遇','upload/banner-05.webp','URL','/pages/profile/index',50,'ACTIVE',NOW(3));

-- 修复客户端乱码店长，并让客户端已有的四位技师在管理端有同一份资料。
UPDATE staff SET name='林雅静', avatar_url='upload/staff-01.webp', updated_at=NOW(3) WHERE id=1;
UPDATE staff SET avatar_url='upload/staff-02.webp', updated_at=NOW(3) WHERE id=2;
UPDATE staff SET avatar_url='upload/staff-03.webp', updated_at=NOW(3) WHERE staff_no='m1';
UPDATE staff SET avatar_url='upload/staff-04.webp', updated_at=NOW(3) WHERE staff_no='m2';
DELETE FROM weixiujishi WHERE id IN (2607230201,2607230202,2607230211,2607230212,2607230213,2607230214,2607230215);
INSERT INTO weixiujishi (id,addtime,weixiuzhanghao,mima,weixiuxingming,xingbie,lianxidianhua,touxiang) VALUES
(2607230201,NOW(),'S0001','123456','林雅静','女','13800001001','upload/staff-01.webp'),
(2607230202,NOW(),'S0002','123456','安然','女','13800001002','upload/staff-02.webp'),
(2607230211,NOW(),'FACE_JS01','123456','苏婉清','女','13800001101','upload/staff-05.webp'),
(2607230212,NOW(),'FACE_JS02','123456','顾念','女','13800001102','upload/staff-06.webp'),
(2607230213,NOW(),'FACE_JS03','123456','沈知夏','女','13800001103','upload/member-01.webp'),
(2607230214,NOW(),'FACE_JS04','123456','周予安','女','13800001104','upload/member-02.webp'),
(2607230215,NOW(),'FACE_JS05','123456','许清禾','女','13800001105','upload/member-03.webp');
UPDATE weixiujishi SET touxiang='upload/staff-03.webp' WHERE weixiuzhanghao='m1';
UPDATE weixiujishi SET touxiang='upload/staff-04.webp' WHERE weixiuzhanghao='m2';
DELETE FROM staff WHERE id BETWEEN 2607230211 AND 2607230215;
INSERT INTO staff (id,shop_id,staff_no,name,phone,job_role,level_name,avatar_url,bio,hire_date,status,created_at,updated_at) VALUES
(2607230211,1,'FACE_JS01','苏婉清','13800001101','BEAUTICIAN','高级美容师','upload/staff-05.webp','擅长敏感肌修护与屏障管理','2022-03-08','ACTIVE',NOW(3),NOW(3)),
(2607230212,1,'FACE_JS02','顾念','13800001102','BEAUTICIAN','资深美容师','upload/staff-06.webp','擅长面部轮廓护理与舒缓按摩','2021-06-18','ACTIVE',NOW(3),NOW(3)),
(2607230213,1,'FACE_JS03','沈知夏','13800001103','BEAUTICIAN','高级美容师','upload/member-01.webp','专注油痘肌与毛孔精细管理','2023-02-11','ACTIVE',NOW(3),NOW(3)),
(2607230214,1,'FACE_JS04','周予安','13800001104','BEAUTICIAN','美容顾问','upload/member-02.webp','擅长肌肤检测与居家护理方案','2024-01-22','ACTIVE',NOW(3),NOW(3)),
(2607230215,1,'FACE_JS05','许清禾','13800001105','BEAUTICIAN','芳疗美容师','upload/member-03.webp','擅长芳香舒压与身体护理','2022-09-15','ACTIVE',NOW(3),NOW(3));

DELETE FROM chezhu WHERE id BETWEEN 2607230301 AND 2607230305;
INSERT INTO chezhu (id,addtime,zhanghao,mima,xingming,xingbie,shouji,touxiang) VALUES
(2607230301,NOW(),'FACE_HY01','123456','唐可心','女','13900002001','upload/member-extra-01.webp'),
(2607230302,NOW(),'FACE_HY02','123456','叶舒颜','女','13900002002','upload/member-extra-02.webp'),
(2607230303,NOW(),'FACE_HY03','123456','陆星晚','女','13900002003','upload/member-extra-03.webp'),
(2607230304,NOW(),'FACE_HY04','123456','温以宁','女','13900002004','upload/member-extra-04.webp'),
(2607230305,NOW(),'FACE_HY05','123456','乔雨薇','女','13900002005','upload/member-extra-05.webp');

DELETE FROM pinpaixinxi WHERE id BETWEEN 2607230401 AND 2607230405;
INSERT INTO pinpaixinxi (id,addtime,pinpai) VALUES
(2607230401,NOW(),'雅漾'),(2607230402,NOW(),'薇诺娜'),(2607230403,NOW(),'修丽可'),(2607230404,NOW(),'珂润'),(2607230405,NOW(),'理肤泉');

DELETE FROM fuwufenlei WHERE id BETWEEN 2607230501 AND 2607230505;
INSERT INTO fuwufenlei (id,addtime,fuwufenlei) VALUES
(2607230501,NOW(),'补水保湿'),(2607230502,NOW(),'敏感修护'),(2607230503,NOW(),'清洁管理'),(2607230504,NOW(),'紧致抗老'),(2607230505,NOW(),'芳香舒压');

DELETE FROM guzhangfenlei WHERE id BETWEEN 2607230601 AND 2607230605;
INSERT INTO guzhangfenlei (id,addtime,guzhangfenlei) VALUES
(2607230601,NOW(),'干燥缺水'),(2607230602,NOW(),'敏感泛红'),(2607230603,NOW(),'油脂旺盛'),(2607230604,NOW(),'色素沉积'),(2607230605,NOW(),'松弛细纹');

DELETE FROM shouhoufuwu WHERE id BETWEEN 2607230701 AND 2607230705;
INSERT INTO shouhoufuwu (id,addtime,fuwumingcheng,fuwufenlei,jiage,fengmian,fabushijian,fuwuxiangqing,weixiuzhanghao,weixiuxingming,clicktime,clicknum,storeupnum) VALUES
(2607230701,NOW(),'云感深层补水','补水保湿',298,'upload/service-01.webp',NOW(),'温和清洁、补水导入与锁水修护','FACE_JS01','苏婉清',NOW(),36,8),
(2607230702,NOW(),'舒缓屏障修护','敏感修护',368,'upload/service-02.webp',NOW(),'针对敏感泛红的分层舒缓护理','FACE_JS02','顾念',NOW(),42,12),
(2607230703,NOW(),'净透毛孔管理','清洁管理',328,'upload/service-03.webp',NOW(),'软化角质并完成温和深层清洁','FACE_JS03','沈知夏',NOW(),31,6),
(2607230704,NOW(),'胶原紧致护理','紧致抗老',498,'upload/service-04.webp',NOW(),'紧致按摩配合高效精华导入','FACE_JS04','周予安',NOW(),55,15),
(2607230705,NOW(),'芳香肩颈舒压','芳香舒压',268,'upload/service-05.webp',NOW(),'芳疗精油配合肩颈经络舒压','FACE_JS05','许清禾',NOW(),28,9);

DELETE FROM peijianxinxi WHERE id BETWEEN 2607230801 AND 2607230805;
INSERT INTO peijianxinxi (id,addtime,peijianbianhao,peijianmingcheng,peijianzhonglei,pinpai,shuliang,shoujia,tupian,dengjiriqi,peijianjieshao) VALUES
(2607230801,NOW(),'CP2601','舒缓修护精华','护理精华','薇诺娜',40,228,'upload/product-01.webp',CURDATE(),'敏感肌日常修护精华'),
(2607230802,NOW(),'CP2602','玻尿酸补水面膜','护理面膜','FACE精选',60,99,'upload/product-02.webp',CURDATE(),'高保湿贴片面膜五片装'),
(2607230803,NOW(),'CP2603','氨基酸洁面乳','清洁产品','珂润',35,128,'upload/product-03.webp',CURDATE(),'温和清洁不紧绷'),
(2607230804,NOW(),'CP2604','焕亮防护乳','防护产品','理肤泉',28,198,'upload/product-04.webp',CURDATE(),'轻薄日间防护乳'),
(2607230805,NOW(),'CP2605','植物按摩精油','芳疗用品','FACE精选',22,168,'upload/product-05.webp',CURDATE(),'用于身体与肩颈舒压护理');

DELETE FROM peijianchuku WHERE id BETWEEN 2607230901 AND 2607230905;
INSERT INTO peijianchuku (id,addtime,chukubianhao,peijianmingcheng,peijianzhonglei,shoujia,shuliang,zongjia,kehu,chukushijian,beizhu,weixiuzhanghao,weixiuxingming) VALUES
(2607230901,NOW(),'CK2601','舒缓修护精华','护理精华',228,2,456,'唐可心',NOW(),'疗程配套领用','FACE_JS01','苏婉清'),
(2607230902,NOW(),'CK2602','玻尿酸补水面膜','护理面膜',99,3,297,'叶舒颜',NOW(),'会员居家护理','FACE_JS02','顾念'),
(2607230903,NOW(),'CK2603','氨基酸洁面乳','清洁产品',128,1,128,'陆星晚',NOW(),'疗程后购买','FACE_JS03','沈知夏'),
(2607230904,NOW(),'CK2604','焕亮防护乳','防护产品',198,1,198,'温以宁',NOW(),'日间防护建议','FACE_JS04','周予安'),
(2607230905,NOW(),'CK2605','植物按摩精油','芳疗用品',168,2,336,'乔雨薇',NOW(),'芳疗套餐配套','FACE_JS05','许清禾');

DELETE FROM fuwuyuyue WHERE id BETWEEN 2607231001 AND 2607231005;
INSERT INTO fuwuyuyue (id,addtime,yuyuebianhao,fuwumingcheng,fuwufenlei,fengmian,jiage,weixiuzhuangtai,yuyueshijian,cheliangwenti,zhanghao,xingming,shouji,chepaihao,weixiuzhanghao,weixiuxingming,sfsh,shhf) VALUES
(2607231001,NOW(),'YY2601','云感深层补水','补水保湿','upload/experience-01.webp',298,'已确认',DATE_ADD(NOW(),INTERVAL 1 DAY),'近期皮肤干燥','FACE_HY01','唐可心','13900002001','SKIN-001','FACE_JS01','苏婉清','是','预约已确认'),
(2607231002,NOW(),'YY2602','舒缓屏障修护','敏感修护','upload/experience-02.webp',368,'待服务',DATE_ADD(NOW(),INTERVAL 2 DAY),'换季轻微泛红','FACE_HY02','叶舒颜','13900002002','SKIN-002','FACE_JS02','顾念','是','预约已确认'),
(2607231003,NOW(),'YY2603','净透毛孔管理','清洁管理','upload/experience-03.webp',328,'已确认',DATE_ADD(NOW(),INTERVAL 3 DAY),'T区油脂较多','FACE_HY03','陆星晚','13900002003','SKIN-003','FACE_JS03','沈知夏','是','预约已确认'),
(2607231004,NOW(),'YY2604','胶原紧致护理','紧致抗老','upload/experience-04.webp',498,'待确认',DATE_ADD(NOW(),INTERVAL 4 DAY),'希望改善细纹','FACE_HY04','温以宁','13900002004','SKIN-004','FACE_JS04','周予安','否','等待确认'),
(2607231005,NOW(),'YY2605','芳香肩颈舒压','芳香舒压','upload/experience-05.webp',268,'已确认',DATE_ADD(NOW(),INTERVAL 5 DAY),'肩颈疲劳紧张','FACE_HY05','乔雨薇','13900002005','SKIN-005','FACE_JS05','许清禾','是','预约已确认');

DELETE FROM weixiujilu WHERE id BETWEEN 2607231101 AND 2607231105;
INSERT INTO weixiujilu (id,addtime,weixiubianhao,fuwumingcheng,fuwufenlei,fengmian,jiage,peijianmingcheng,allshoujia,zongjia,weixiushijian,weixiushuoming,zhanghao,xingming,shouji,chepaihao,weixiuzhanghao,weixiuxingming,ispay) VALUES
(2607231101,NOW(),'JL2601','云感深层补水','补水保湿','upload/service-record-01.webp',298,'舒缓修护精华',228,526,NOW(),'完成补水导入，肤感稳定','FACE_HY01','唐可心','13900002001','SKIN-001','FACE_JS01','苏婉清','已支付'),
(2607231102,NOW(),'JL2602','舒缓屏障修护','敏感修护','upload/service-record-02.webp',368,'玻尿酸补水面膜',99,467,NOW(),'泛红明显缓解，建议持续修护','FACE_HY02','叶舒颜','13900002002','SKIN-002','FACE_JS02','顾念','已支付'),
(2607231103,NOW(),'JL2603','净透毛孔管理','清洁管理','upload/service-record-03.webp',328,'氨基酸洁面乳',128,456,NOW(),'完成温和清洁，无刺激反应','FACE_HY03','陆星晚','13900002003','SKIN-003','FACE_JS03','沈知夏','已支付'),
(2607231104,NOW(),'JL2604','胶原紧致护理','紧致抗老','upload/service-record-04.webp',498,'焕亮防护乳',198,696,NOW(),'轮廓紧致度提升，建议四周复诊','FACE_HY04','温以宁','13900002004','SKIN-004','FACE_JS04','周予安','已支付'),
(2607231105,NOW(),'JL2605','芳香肩颈舒压','芳香舒压','upload/service-record-05.webp',268,'植物按摩精油',168,436,NOW(),'肩颈放松良好，无不适','FACE_HY05','乔雨薇','13900002005','SKIN-005','FACE_JS05','许清禾','已支付');

DELETE FROM pingjiafankui WHERE id BETWEEN 2607231201 AND 2607231205;
INSERT INTO pingjiafankui (id,addtime,pingjiabianhao,fuwumingcheng,fengmian,fuwupingjia,manyichengdu,pingjiafankui,pingjiashijian,zhanghao,xingming,weixiuzhanghao,weixiuxingming,crossuserid,crossrefid) VALUES
(2607231201,NOW(),'PJ2601','云感深层补水','upload/equipment-01.webp','流程细致，补水效果明显','非常满意','环境安静舒适',NOW(),'FACE_HY01','唐可心','FACE_JS01','苏婉清',2607230301,2607230701),
(2607231202,NOW(),'PJ2602','舒缓屏障修护','upload/equipment-02.webp','手法温和，泛红缓解','非常满意','会继续预约疗程',NOW(),'FACE_HY02','叶舒颜','FACE_JS02','顾念',2607230302,2607230702),
(2607231203,NOW(),'PJ2603','净透毛孔管理','upload/equipment-03.webp','清洁到位且没有刺激','满意','护理建议很实用',NOW(),'FACE_HY03','陆星晚','FACE_JS03','沈知夏',2607230303,2607230703),
(2607231204,NOW(),'PJ2604','胶原紧致护理','upload/equipment-04.webp','按摩专业，轮廓更紧致','非常满意','服务体验很好',NOW(),'FACE_HY04','温以宁','FACE_JS04','周予安',2607230304,2607230704),
(2607231205,NOW(),'PJ2605','芳香肩颈舒压','upload/equipment-05.webp','精油香气舒适，放松明显','满意','希望增加晚间场次',NOW(),'FACE_HY05','乔雨薇','FACE_JS05','许清禾',2607230305,2607230705);

DELETE FROM guzhangpaicha WHERE id BETWEEN 2607231301 AND 2607231305;
INSERT INTO guzhangpaicha (id,addtime,guzhangmingcheng,guzhangfenlei,guzhangyuanyin,fengmian,paichabujian,guzhangpaicha,fabushijian,clicktime,clicknum,storeupnum) VALUES
(2607231301,NOW(),'季节性干燥脱屑','干燥缺水','环境湿度降低与保湿不足','upload/knowledge-01.webp','含水量与屏障状态','加强补水并减少过度清洁',NOW(),NOW(),18,3),
(2607231302,NOW(),'面颊反复泛红','敏感泛红','屏障受损与刺激叠加','upload/knowledge-02.webp','敏感区域与产品使用史','暂停刺激成分并进行舒缓修护',NOW(),NOW(),26,6),
(2607231303,NOW(),'T区油脂旺盛','油脂旺盛','水油失衡与清洁不当','upload/knowledge-03.webp','油脂分布与毛孔状态','温和清洁并分区保湿',NOW(),NOW(),21,4),
(2607231304,NOW(),'肤色暗沉不均','色素沉积','防护不足与代谢缓慢','upload/knowledge-04.webp','色素分布与日晒记录','做好防护并逐步焕亮',NOW(),NOW(),33,9),
(2607231305,NOW(),'轮廓松弛细纹','松弛细纹','胶原流失与表情牵拉','upload/knowledge-05.webp','弹性与细纹位置','紧致护理结合长期居家管理',NOW(),NOW(),29,7);

DELETE FROM xinnengyuanqiche WHERE id BETWEEN 2607231401 AND 2607231405;
INSERT INTO xinnengyuanqiche (id,addtime,qichexinghao,qicheleixing,pinpai,baigonglijiasu,zuigaoshisu,xuhanggonglishu,xiaolv,jiage,zuoweishu,donglizongcheng,chongdianchatou,fengmian,waixingshiyang,chongdianfangan,jishuguige,xiangxijieshao,clicktime,clicknum,storeupnum) VALUES
(2607231401,NOW(),'水氧焕肤仪 A1','补水设备','FACE设备','3分钟启动','三档','连续6小时','高效',12800,1,'水氧导入','Type-C','upload/banner-06.webp','白色流线机身','两小时充满','雾化粒径可调','用于补水精华均匀导入',NOW(),12,2),
(2607231402,NOW(),'光谱修护仪 L2','光疗设备','FACE设备','即开即用','五档','连续5小时','节能',18900,1,'LED光谱','专用接口','upload/service-06.webp','香槟金机身','三小时充满','红蓝黄三色光谱','用于舒缓与状态管理',NOW(),15,4),
(2607231403,NOW(),'智能肌肤检测仪 S3','检测设备','FACE设备','30秒检测','专业版','连续8小时','高效',26800,1,'多光谱成像','Type-C','upload/package-06.webp','深灰金属机身','四小时充满','高清成像与数据分析','用于建立会员肌肤档案',NOW(),22,7),
(2607231404,NOW(),'温感导入仪 W4','导入设备','FACE设备','1分钟预热','四档','连续4小时','稳定',9800,1,'恒温导入','Type-C','upload/product-06.webp','珍珠白机身','两小时充满','恒温与微振双模式','辅助精华吸收与按摩',NOW(),17,3),
(2607231405,NOW(),'微电紧致仪 M5','紧致设备','FACE设备','即开即用','六档','连续5小时','高效',22900,1,'微电流','专用接口','upload/knowledge-06.webp','银灰机身','三小时充满','强度分级与安全保护','用于面部轮廓护理',NOW(),25,8);

DELETE FROM weixiuziliao WHERE id BETWEEN 2607231501 AND 2607231505;
INSERT INTO weixiuziliao (id,addtime,ziliaomingcheng,ziliaowenjian,fengmian,shangchuanshijian,ziliaoneirong) VALUES
(2607231501,NOW(),'敏感肌屏障修护手册','', 'upload/member-04.webp',NOW(),'敏感期间应减少刺激性成分，建立精简稳定的护理步骤。'),
(2607231502,NOW(),'科学补水与保湿指南','', 'upload/member-05.webp',NOW(),'补水与锁水需要结合，按肌肤状态选择合适的保湿体系。'),
(2607231503,NOW(),'油痘肌清洁管理规范','', 'upload/member-06.webp',NOW(),'避免过度清洁，关注清洁频率、产品刺激性与后续保湿。'),
(2607231504,NOW(),'美容仪器消毒流程','', 'upload/member-extra-06.webp',NOW(),'服务前后按设备材质完成清洁、消毒、登记与状态检查。'),
(2607231505,NOW(),'客户服务记录标准','', 'upload/service-record-06.webp',NOW(),'记录肌肤状态、服务步骤、即时反应与下次到店建议。');

DELETE FROM chat WHERE id BETWEEN 2607231601 AND 2607231605;
INSERT INTO chat (id,addtime,userid,adminid,ask,reply,isreply,isread,uname,uimage,type) VALUES
(2607231601,NOW(),2,1,'敏感肌可以做补水护理吗？','可以，建议先做肌肤检测后选择舒缓型方案。',1,1,'唐可心','upload/member-extra-01.webp',1),
(2607231602,NOW(),2,1,'预约可以提前多久取消？','到店前四小时可免费取消或改期。',1,1,'叶舒颜','upload/member-extra-02.webp',1),
(2607231603,NOW(),2,1,'第一次到店需要准备什么？','无需特殊准备，建议素颜或淡妆到店。',1,1,'陆星晚','upload/member-extra-03.webp',1),
(2607231604,NOW(),2,1,'护理后当天可以化妆吗？','建议让肌肤休息，至少四小时后再化淡妆。',1,1,'温以宁','upload/member-extra-04.webp',1),
(2607231605,NOW(),2,1,'会员积分如何使用？','结算时可按当前会员规则抵扣指定项目。',1,1,'乔雨薇','upload/member-extra-05.webp',1);
