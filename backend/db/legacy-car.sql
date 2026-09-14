-- MySQL dump 10.13  Distrib 8.0.41, for Win64 (x86_64)
--
-- Host: 127.0.0.1    Database: car
-- ------------------------------------------------------
-- Server version	5.7.32

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `chat`
--

DROP TABLE IF EXISTS `chat`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `chat` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `addtime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `userid` bigint(20) NOT NULL COMMENT '用户id',
  `adminid` bigint(20) DEFAULT NULL COMMENT '管理员id',
  `ask` longtext COLLATE utf8mb4_unicode_ci COMMENT '提问',
  `reply` longtext COLLATE utf8mb4_unicode_ci COMMENT '回复',
  `isreply` int(11) DEFAULT NULL COMMENT '是否回复',
  `isread` int(11) DEFAULT '0' COMMENT '已读/未读(1:已读,0:未读)',
  `uname` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '用户头像',
  `uimage` longtext COLLATE utf8mb4_unicode_ci COMMENT '用户名',
  `type` int(11) DEFAULT '1' COMMENT '内容类型(1:文本,2:图片,3:视频,4:文件,5:表情)',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=15 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='在线咨询';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `chat`
--

LOCK TABLES `chat` WRITE;
/*!40000 ALTER TABLE `chat` DISABLE KEYS */;
INSERT INTO `chat` VALUES (1,'2025-12-22 09:12:04',1,1,'提问1','回复1',1,1,'用户头像1','upload/obsidian-mechanic-portrait.webp',1),(2,'2025-12-22 09:12:04',2,2,'提问2','回复2',2,2,'用户头像2','upload/obsidian-mechanic-portrait.webp',2),(3,'2025-12-22 09:12:04',3,3,'提问3','回复3',3,3,'用户头像3','upload/obsidian-mechanic-portrait.webp',3),(4,'2025-12-22 09:12:04',4,4,'提问4','回复4',4,4,'用户头像4','upload/obsidian-mechanic-portrait.webp',4),(5,'2025-12-22 09:12:04',5,5,'提问5','回复5',5,5,'用户头像5','upload/obsidian-mechanic-portrait.webp',5),(6,'2025-12-22 09:12:04',6,6,'提问6','回复6',6,6,'用户头像6','upload/obsidian-mechanic-portrait.webp',6),(7,'2025-12-22 09:12:04',7,7,'提问7','回复7',7,7,'用户头像7','upload/obsidian-mechanic-portrait.webp',7),(8,'2025-12-22 09:12:04',8,8,'提问8','回复8',8,8,'用户头像8','upload/obsidian-mechanic-portrait.webp',8),(9,'2025-12-22 09:15:23',1734340516997,NULL,'1122',NULL,0,1,'11','upload/1734340516424.jpg',1),(10,'2025-12-22 09:19:18',1734340516997,1,NULL,'回复回复AA444',NULL,1,'admin','upload/image1.jpg',1),(11,'2026-04-13 11:47:51',11,NULL,'111',NULL,0,1,'账号1','upload/chezhu_touxiang1.jpg',1),(12,'2026-04-13 11:48:00',11,1,NULL,'222',0,1,'admin','upload/image1.jpg',1),(13,'2026-04-13 11:54:04',11,NULL,'333',NULL,0,1,'账号1','upload/chezhu_touxiang1.jpg',1),(14,'2026-04-13 11:54:10',11,1,NULL,'444',NULL,1,'admin','upload/image1.jpg',1);
/*!40000 ALTER TABLE `chat` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `chatmessage`
--

DROP TABLE IF EXISTS `chatmessage`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `chatmessage` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `addtime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `uid` bigint(20) NOT NULL COMMENT '用户ID',
  `fid` bigint(20) NOT NULL COMMENT '好友用户ID',
  `content` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '内容',
  `format` int(11) DEFAULT NULL COMMENT '格式(1:文字，2:图片)',
  `isread` int(11) DEFAULT '0' COMMENT '消息已读(0:未读，1:已读)',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `chatmessage`
--

LOCK TABLES `chatmessage` WRITE;
/*!40000 ALTER TABLE `chatmessage` DISABLE KEYS */;
INSERT INTO `chatmessage` VALUES (1,'2025-12-22 09:19:58',1734340516997,1734340561566,'1222',1,1),(2,'2025-12-22 09:19:59',1734340516997,1734340561566,'upload/1734340637658.jpg',2,1),(3,'2025-12-22 09:20:50',1734340561566,1734340516997,'哈哈4444',1,1);
/*!40000 ALTER TABLE `chatmessage` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `chezhu`
--

DROP TABLE IF EXISTS `chezhu`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `chezhu` (
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
) ENGINE=InnoDB AUTO_INCREMENT=1778137948420 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='车主';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `chezhu`
--

LOCK TABLES `chezhu` WRITE;
/*!40000 ALTER TABLE `chezhu` DISABLE KEYS */;
INSERT INTO `chezhu` VALUES (11,'2025-12-22 09:12:03','账号1','123456','姓名1','男','13823888881','upload/obsidian-mechanic-portrait.webp'),(12,'2025-12-22 09:12:03','账号2','123456','姓名2','男','13823888882','upload/obsidian-mechanic-portrait.webp'),(13,'2025-12-22 09:12:03','账号3','123456','姓名3','男','13823888883','upload/obsidian-mechanic-portrait.webp'),(14,'2025-12-22 09:12:03','账号4','123456','姓名4','男','13823888884','upload/obsidian-mechanic-portrait.webp'),(15,'2025-12-22 09:12:03','账号5','123456','姓名5','男','13823888885','upload/obsidian-mechanic-portrait.webp'),(16,'2025-12-22 09:12:03','账号6','123456','姓名6','男','13823888886','upload/obsidian-mechanic-portrait.webp'),(17,'2025-12-22 09:12:03','账号7','123456','姓名7','男','13823888887','upload/obsidian-mechanic-portrait.webp'),(18,'2025-12-22 09:12:03','账号8','123456','姓名8','女','13823888888','upload/obsidian-mechanic-portrait.webp'),(1734340516997,'2025-12-22 09:15:17','11','11','张三','女','15111111111','upload/obsidian-mechanic-portrait.webp'),(1778137220100,'2026-05-07 07:00:20','5555','123456','dzy','男','13802383979',''),(1778137948419,'2026-05-07 07:12:28','wkb','123456','ddd','男','15338361290','');
/*!40000 ALTER TABLE `chezhu` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `config`
--

DROP TABLE IF EXISTS `config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `config` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(100) NOT NULL COMMENT '配置参数名称',
  `value` varchar(100) DEFAULT NULL COMMENT '配置参数值',
  `url` varchar(500) DEFAULT NULL COMMENT 'url',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8 COMMENT='配置文件';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `config`
--

LOCK TABLES `config` WRITE;
/*!40000 ALTER TABLE `config` DISABLE KEYS */;
INSERT INTO `config` VALUES (1,'picture1','upload/obsidian-service-workshop.webp',NULL),(2,'picture2','upload/obsidian-service-workshop.webp',NULL),(3,'picture3','upload/obsidian-service-workshop.webp',NULL);
/*!40000 ALTER TABLE `config` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `friend`
--

DROP TABLE IF EXISTS `friend`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `friend` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `addtime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `uid` bigint(20) NOT NULL COMMENT '用户ID',
  `fid` bigint(20) NOT NULL COMMENT '好友用户ID',
  `name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '名称',
  `picture` longtext COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '图片',
  `role` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '角色',
  `tablename` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '表名',
  `alias` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '别名',
  `type` int(11) DEFAULT '0' COMMENT '类型(0:好友申请，1:好友，2:消息)',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='好友表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `friend`
--

LOCK TABLES `friend` WRITE;
/*!40000 ALTER TABLE `friend` DISABLE KEYS */;
INSERT INTO `friend` VALUES (1,'2025-12-22 09:19:58',1734340516997,1734340561566,'22','upload/obsidian-mechanic-portrait.webp',NULL,'weixiujishi',NULL,2),(2,'2025-12-22 09:19:58',1734340561566,1734340516997,'11','upload/obsidian-mechanic-portrait.webp',NULL,'chezhu',NULL,2);
/*!40000 ALTER TABLE `friend` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `fuwufenlei`
--

DROP TABLE IF EXISTS `fuwufenlei`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `fuwufenlei` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `addtime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `fuwufenlei` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '服务分类',
  PRIMARY KEY (`id`),
  UNIQUE KEY `fuwufenlei` (`fuwufenlei`),
  KEY `fuwufenlei_u02v` (`fuwufenlei`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='服务分类';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `fuwufenlei`
--

LOCK TABLES `fuwufenlei` WRITE;
/*!40000 ALTER TABLE `fuwufenlei` DISABLE KEYS */;
INSERT INTO `fuwufenlei` VALUES (1,'2025-12-22 09:12:03','服务分类1'),(2,'2025-12-22 09:12:03','服务分类2'),(3,'2025-12-22 09:12:03','服务分类3'),(4,'2025-12-22 09:12:03','服务分类4'),(5,'2025-12-22 09:12:03','服务分类5'),(6,'2025-12-22 09:12:03','服务分类6'),(7,'2025-12-22 09:12:03','服务分类7'),(8,'2025-12-22 09:12:03','维修'),(9,'2025-12-22 09:16:53','保养');
/*!40000 ALTER TABLE `fuwufenlei` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `fuwuyuyue`
--

DROP TABLE IF EXISTS `fuwuyuyue`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `fuwuyuyue` (
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
) ENGINE=InnoDB AUTO_INCREMENT=18 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='服务预约';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `fuwuyuyue`
--

LOCK TABLES `fuwuyuyue` WRITE;
/*!40000 ALTER TABLE `fuwuyuyue` DISABLE KEYS */;
INSERT INTO `fuwuyuyue` VALUES (1,'2025-12-22 09:12:03','1111111111','服务名称1','服务分类1','upload/obsidian-service-workshop.webp',1,'已维修','2025-12-22 17:12:03','车辆问题1','账号1','姓名1','手机1','车牌号1','维修账号1','维修姓名1','是',''),(2,'2025-12-22 09:12:03','2222222222','服务名称2','服务分类2','upload/obsidian-service-workshop.webp',2,'已维修','2025-12-22 17:12:03','车辆问题2','账号2','姓名2','手机2','车牌号2','维修账号2','维修姓名2','是',''),(3,'2025-12-22 09:12:03','3333333333','服务名称3','服务分类3','upload/obsidian-service-workshop.webp',3,'已维修','2025-12-22 17:12:03','车辆问题3','账号3','姓名3','手机3','车牌号3','维修账号3','维修姓名3','是',''),(4,'2025-12-22 09:12:03','4444444444','服务名称4','服务分类4','upload/obsidian-service-workshop.webp',4,'已维修','2025-12-22 17:12:03','车辆问题4','账号4','姓名4','手机4','车牌号4','维修账号4','维修姓名4','是',''),(5,'2025-12-22 09:12:03','5555555555','服务名称5','服务分类5','upload/obsidian-service-workshop.webp',5,'已维修','2025-12-22 17:12:03','车辆问题5','账号5','姓名5','手机5','车牌号5','维修账号5','维修姓名5','是',''),(6,'2025-12-22 09:12:03','6666666666','服务名称6','服务分类6','upload/obsidian-service-workshop.webp',6,'已维修','2025-12-22 17:12:03','车辆问题6','账号6','姓名6','手机6','车牌号6','维修账号6','维修姓名6','是',''),(7,'2025-12-22 09:12:03','7777777777','服务名称7','服务分类7','upload/obsidian-service-workshop.webp',7,'已维修','2025-12-22 17:12:03','车辆问题7','账号7','姓名7','手机7','车牌号7','维修账号7','维修姓名7','是',''),(8,'2025-12-22 09:12:03','8888888888','服务名称8','服务分类8','upload/obsidian-service-workshop.webp',8,'已维修','2025-12-22 17:12:03','车辆问题8','账号8','姓名8','手机8','车牌号8','维修账号8','维修姓名8','是',''),(9,'2025-12-22 09:20:38','1734340831581','第一服务AA','维修','upload/obsidian-service-workshop.webp',22,'已维修','2024-12-17 17:20:31','<p>操作者可以在输入框输入&nbsp;&nbsp;详情信息&nbsp;&nbsp;等内容。</p><p>操作者可以在输入框输入&nbsp;&nbsp;详情信息&nbsp;&nbsp;等内容。</p><p>操作者可以在输入框输入&nbsp;&nbsp;详情信息&nbsp;&nbsp;等内容。</p><p>操作者可以在输入框输入&nbsp;&nbsp;详情信息&nbsp;&nbsp;等内容。</p><p><br></p><p>操作者可以在输入框输入&nbsp;&nbsp;详情信息&nbsp;&nbsp;等内容。</p><p><br></p><p>操作者可以在输入框输入&nbsp;&nbsp;详情信息&nbsp;&nbsp;等内容。</p><p>操作者可以在输入框输入&nbsp;&nbsp;详情信息&nbsp;&nbsp;等内容。</p><p>操作者可以在输入框输入&nbsp;&nbsp;详情信息&nbsp;&nbsp;等内容。</p><p>操作者可以在输入框输入&nbsp;&nbsp;详情信息&nbsp;&nbsp;等内容。</p><p>操作者可以在输入框输入&nbsp;&nbsp;详情信息&nbsp;&nbsp;等内容。</p><p>操作者可以在输入框输入&nbsp;&nbsp;详情信息&nbsp;&nbsp;等内容。</p><p>操作者可以在输入框输入&nbsp;&nbsp;详情信息&nbsp;&nbsp;等内容。</p><p><br></p>','11','张三','15111111111','京W45125','22','李四','是','122'),(10,'2026-04-09 14:27:05','1775744809393','服务名称5','服务分类5','upload/obsidian-service-workshop.webp',5,'已维修','2026-04-09 22:26:49','<p>哇哇哇哇哇哇哇哇哇哇哇哇</p>','账号2','姓名2','13823888882','1113','维修账号5','维修姓名5','是','无'),(11,'2026-04-13 11:54:45','1776081273384','服务名称1','服务分类1','upload/obsidian-service-workshop.webp',1,'已维修','2026-04-13 19:54:33','<p>ww</p>','账号1','姓名1','13823888881','444','维修账号1','维修姓名1','是','888'),(12,'2026-04-18 01:05:49','1776474338289','第一服务AA','维修','upload/obsidian-service-workshop.webp',22,'未维修','2026-04-18 09:05:38','','账号1','姓名1','13823888881','555','22','李四','待审核',NULL),(13,'2026-04-18 01:11:39','1776474639600','服务名称1','服务分类1','upload/obsidian-service-workshop.webp',1,'已维修','2026-04-18 09:10:39','','账号1','姓名1','13823888881','11','维修账号1','维修姓名1','是','11'),(14,'2026-04-22 11:33:07','1776857457159','服务名称1','服务分类1','upload/obsidian-service-workshop.webp',1,'已维修','2026-04-22 19:30:57','<p>666</p>','账号1','姓名1','13823888881','666','维修账号1','维修姓名1','是','9'),(15,'2026-04-22 12:06:45','1776859600601','服务名称2','服务分类2','upload/obsidian-service-workshop.webp',2,'未维修','2026-04-22 20:06:40','<p>123123</p>','账号1','姓名1','13823888881','3123','维修账号2','维修姓名2','待审核',NULL),(16,'2026-05-07 07:13:23','1778137992194','服务名称1','服务分类1','upload/obsidian-service-workshop.webp',1,'已维修','2026-05-07 15:13:12','<p>11111</p>','wkb','ddd','15338361290','445455','维修账号1','维修姓名1','是','11'),(17,'2026-05-10 06:24:35','1778394265120','服务名称1','服务分类1','upload/obsidian-service-workshop.webp',1,'已维修','2026-05-10 14:24:25','<p>79789</p>','账号1','姓名1','13823888881','879798','维修账号1','维修姓名1','是','9');
/*!40000 ALTER TABLE `fuwuyuyue` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `guzhangfenlei`
--

DROP TABLE IF EXISTS `guzhangfenlei`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `guzhangfenlei` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `addtime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `guzhangfenlei` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '故障分类',
  PRIMARY KEY (`id`),
  UNIQUE KEY `guzhangfenlei` (`guzhangfenlei`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='故障分类';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `guzhangfenlei`
--

LOCK TABLES `guzhangfenlei` WRITE;
/*!40000 ALTER TABLE `guzhangfenlei` DISABLE KEYS */;
INSERT INTO `guzhangfenlei` VALUES (1,'2025-12-22 09:12:03','故障分类1'),(2,'2025-12-22 09:12:03','故障分类2'),(3,'2025-12-22 09:12:03','故障分类3'),(4,'2025-12-22 09:12:03','故障分类4'),(5,'2025-12-22 09:12:03','故障分类5'),(6,'2025-12-22 09:12:03','故障分类6'),(7,'2025-12-22 09:12:03','故障分类7'),(8,'2025-12-22 09:12:04','C类');
/*!40000 ALTER TABLE `guzhangfenlei` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `guzhangpaicha`
--

DROP TABLE IF EXISTS `guzhangpaicha`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `guzhangpaicha` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `addtime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `guzhangmingcheng` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '故障名称',
  `guzhangfenlei` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '故障分类',
  `guzhangyuanyin` longtext COLLATE utf8mb4_unicode_ci COMMENT '故障原因',
  `fengmian` longtext COLLATE utf8mb4_unicode_ci COMMENT '封面',
  `paichabujian` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '排查部件',
  `guzhangpaicha` longtext COLLATE utf8mb4_unicode_ci COMMENT '故障排查',
  `fabushijian` datetime DEFAULT NULL COMMENT '发布时间',
  `clicktime` datetime DEFAULT NULL COMMENT '最近点击时间',
  `clicknum` int(11) DEFAULT '0' COMMENT '点击次数',
  `storeupnum` int(11) DEFAULT '0' COMMENT '收藏数',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='故障排查';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `guzhangpaicha`
--

LOCK TABLES `guzhangpaicha` WRITE;
/*!40000 ALTER TABLE `guzhangpaicha` DISABLE KEYS */;
INSERT INTO `guzhangpaicha` VALUES (1,'2025-12-22 09:12:03','故障名称1','故障分类1','故障原因1','upload/obsidian-engine-detail.webp','排查部件1','故障排查1','2025-12-22 17:12:03','2025-12-22 17:12:03',1,1),(2,'2025-12-22 09:12:03','故障名称2','故障分类2','故障原因2','upload/obsidian-engine-detail.webp','排查部件2','故障排查2','2025-12-22 17:12:03','2025-12-22 17:12:03',2,2),(3,'2025-12-22 09:12:03','故障名称3','故障分类3','故障原因3','upload/obsidian-engine-detail.webp','排查部件3','故障排查3','2025-12-22 17:12:03','2025-12-22 17:20:22',4,3),(4,'2025-12-22 09:12:03','故障名称4','故障分类4','故障原因4','upload/obsidian-engine-detail.webp','排查部件4','故障排查4','2025-12-22 17:12:03','2025-12-22 17:12:03',4,4),(5,'2025-12-22 09:12:03','故障名称5','故障分类5','故障原因5','upload/obsidian-engine-detail.webp','排查部件5','故障排查5','2025-12-22 17:12:03','2025-12-22 17:12:03',5,5),(6,'2025-12-22 09:12:03','故障名称6','故障分类6','故障原因6','upload/obsidian-engine-detail.webp','排查部件6','故障排查6','2025-12-22 17:12:03','2026-04-18 08:28:38',7,6),(7,'2025-12-22 09:12:03','故障名称7','故障分类7','故障原因7','upload/obsidian-engine-detail.webp','排查部件7','故障排查7','2025-12-22 17:12:03','2026-04-18 08:28:18',8,7),(8,'2025-12-22 09:12:03','故障名称8','故障分类8','故障原因8','upload/obsidian-engine-detail.webp','排查部件8','故障排查8','2025-12-22 17:12:03','2026-04-18 08:28:10',11,8),(9,'2025-12-22 09:18:33','第一故障AAA','C类','操作者可以在输入框输入   简介信息    等内容。','upload/obsidian-engine-detail.webp','发动机','<p>操作者可以在输入框输入&nbsp;&nbsp;详情信息&nbsp;&nbsp;等内容。</p><p>操作者可以在输入框输入&nbsp;&nbsp;详情信息&nbsp;&nbsp;等内容。</p><p>操作者可以在输入框输入&nbsp;&nbsp;详情信息&nbsp;&nbsp;等内容。</p><p>操作者可以在输入框输入&nbsp;&nbsp;详情信息&nbsp;&nbsp;等内容。</p><p><br></p><p>操作者可以在输入框输入&nbsp;&nbsp;详情信息&nbsp;&nbsp;等内容。</p><p><br></p><p>操作者可以在输入框输入&nbsp;&nbsp;详情信息&nbsp;&nbsp;等内容。</p><p>操作者可以在输入框输入&nbsp;&nbsp;详情信息&nbsp;&nbsp;等内容。</p><p>操作者可以在输入框输入&nbsp;&nbsp;详情信息&nbsp;&nbsp;等内容。</p><p>操作者可以在输入框输入&nbsp;&nbsp;详情信息&nbsp;&nbsp;等内容。</p><p>操作者可以在输入框输入&nbsp;&nbsp;详情信息&nbsp;&nbsp;等内容。</p><p>操作者可以在输入框输入&nbsp;&nbsp;详情信息&nbsp;&nbsp;等内容。</p><p>操作者可以在输入框输入&nbsp;&nbsp;详情信息&nbsp;&nbsp;等内容。</p><p><br></p>','2025-12-22 17:18:18','2026-04-22 19:38:46',5,1);
/*!40000 ALTER TABLE `guzhangpaicha` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `peijianchuku`
--

DROP TABLE IF EXISTS `peijianchuku`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `peijianchuku` (
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
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='配件出库';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `peijianchuku`
--

LOCK TABLES `peijianchuku` WRITE;
/*!40000 ALTER TABLE `peijianchuku` DISABLE KEYS */;
INSERT INTO `peijianchuku` VALUES (1,'2025-12-22 09:12:03','1111111111','配件名称1','配件种类1',1,1,1,'客户1','2025-12-22','备注1','维修账号1','维修姓名1'),(2,'2025-12-22 09:12:03','2222222222','配件名称2','配件种类2',2,2,2,'客户2','2025-12-22','备注2','维修账号2','维修姓名2'),(3,'2025-12-22 09:12:03','3333333333','配件名称3','配件种类3',3,3,3,'客户3','2025-12-22','备注3','维修账号3','维修姓名3'),(4,'2025-12-22 09:12:03','4444444444','配件名称4','配件种类4',4,4,4,'客户4','2025-12-22','备注4','维修账号4','维修姓名4'),(5,'2025-12-22 09:12:03','5555555555','配件名称5','配件种类5',5,5,5,'客户5','2025-12-22','备注5','维修账号5','维修姓名5'),(6,'2025-12-22 09:12:03','6666666666','配件名称6','配件种类6',6,6,6,'客户6','2025-12-22','备注6','维修账号6','维修姓名6'),(7,'2025-12-22 09:12:03','7777777777','配件名称7','配件种类7',7,7,7,'客户7','2025-12-22','备注7','维修账号7','维修姓名7'),(8,'2025-12-22 09:12:03','8888888888','配件名称8','配件种类8',8,8,8,'客户8','2025-12-22','备注8','维修账号8','维修姓名8'),(9,'2025-12-22 09:22:16','1734340928458','第一配件','A类',33,5,165,'22','2025-12-22','222','22','李四');
/*!40000 ALTER TABLE `peijianchuku` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `peijianxinxi`
--

DROP TABLE IF EXISTS `peijianxinxi`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `peijianxinxi` (
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
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='配件信息';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `peijianxinxi`
--

LOCK TABLES `peijianxinxi` WRITE;
/*!40000 ALTER TABLE `peijianxinxi` DISABLE KEYS */;
INSERT INTO `peijianxinxi` VALUES (1,'2025-12-22 09:12:03','1111111111','配件名称1','配件种类1','品牌1',0,1,'upload/obsidian-engine-detail.webp','2025-12-22','<p>配件介绍1</p>'),(2,'2025-12-22 09:12:03','2222222222','配件名称2','配件种类2','品牌2',2,2,'upload/obsidian-engine-detail.webp','2025-12-22','配件介绍2'),(3,'2025-12-22 09:12:03','3333333333','配件名称3','配件种类3','品牌3',3,3,'upload/obsidian-engine-detail.webp','2025-12-22','配件介绍3'),(4,'2025-12-22 09:12:03','4444444444','配件名称4','配件种类4','品牌4',4,4,'upload/obsidian-engine-detail.webp','2025-12-22','配件介绍4'),(5,'2025-12-22 09:12:03','5555555555','配件名称5','配件种类5','品牌5',5,5,'upload/obsidian-engine-detail.webp','2025-12-22','配件介绍5'),(6,'2025-12-22 09:12:03','6666666666','配件名称6','配件种类6','品牌6',6,6,'upload/obsidian-engine-detail.webp','2025-12-22','配件介绍6'),(7,'2025-12-22 09:12:03','7777777777','配件名称7','配件种类7','品牌7',7,7,'upload/obsidian-engine-detail.webp','2025-12-22','配件介绍7'),(8,'2025-12-22 09:12:03','8888888888','配件名称8','配件种类8','品牌8',8,8,'upload/obsidian-engine-detail.webp','2025-12-22','配件介绍8'),(9,'2025-12-22 09:17:59','1734340662060','米其林轮胎','轮胎','米其林',17,33,'upload/obsidian-engine-detail.webp','2026-01-15','<p>操作者可以在输入框输入&nbsp;&nbsp;详情信息&nbsp;&nbsp;等内容。</p><p>操作者可以在输入框输入&nbsp;&nbsp;详情信息&nbsp;&nbsp;等内容。</p><p>操作者可以在输入框输入&nbsp;&nbsp;详情信息&nbsp;&nbsp;等内容。</p><p>操作者可以在输入框输入&nbsp;&nbsp;详情信息&nbsp;&nbsp;等内容。</p><p><br></p><p>操作者可以在输入框输入&nbsp;&nbsp;详情信息&nbsp;&nbsp;等内容。</p><p><br></p><p>操作者可以在输入框输入&nbsp;&nbsp;详情信息&nbsp;&nbsp;等内容。</p><p>操作者可以在输入框输入&nbsp;&nbsp;详情信息&nbsp;&nbsp;等内容。</p><p>操作者可以在输入框输入&nbsp;&nbsp;详情信息&nbsp;&nbsp;等内容。</p><p>操作者可以在输入框输入&nbsp;&nbsp;详情信息&nbsp;&nbsp;等内容。</p><p>操作者可以在输入框输入&nbsp;&nbsp;详情信息&nbsp;&nbsp;等内容。</p><p>操作者可以在输入框输入&nbsp;&nbsp;详情信息&nbsp;&nbsp;等内容。</p><p>操作者可以在输入框输入&nbsp;&nbsp;详情信息&nbsp;&nbsp;等内容。</p>');
/*!40000 ALTER TABLE `peijianxinxi` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pingjiafankui`
--

DROP TABLE IF EXISTS `pingjiafankui`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pingjiafankui` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `addtime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `pingjiabianhao` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '评价编号',
  `fuwumingcheng` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '服务名称',
  `fengmian` longtext COLLATE utf8mb4_unicode_ci COMMENT '封面',
  `fuwupingjia` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '服务评价',
  `manyichengdu` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '满意程度',
  `pingjiafankui` longtext COLLATE utf8mb4_unicode_ci COMMENT '评价反馈',
  `pingjiashijian` datetime DEFAULT NULL COMMENT '评价时间',
  `zhanghao` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '账号',
  `xingming` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '姓名',
  `weixiuzhanghao` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '维修账号',
  `weixiuxingming` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '维修姓名',
  `crossuserid` bigint(20) DEFAULT NULL COMMENT '跨表用户id',
  `crossrefid` bigint(20) DEFAULT NULL COMMENT '跨表主键id',
  PRIMARY KEY (`id`),
  UNIQUE KEY `pingjiabianhao` (`pingjiabianhao`)
) ENGINE=InnoDB AUTO_INCREMENT=16 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评价反馈';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pingjiafankui`
--

LOCK TABLES `pingjiafankui` WRITE;
/*!40000 ALTER TABLE `pingjiafankui` DISABLE KEYS */;
INSERT INTO `pingjiafankui` VALUES (1,'2025-12-22 09:12:03','1111111111','服务名称1','upload/obsidian-service-workshop.webp','★','非常满意','评价反馈1','2025-12-22 17:12:03','账号1','姓名1','维修账号1','维修姓名1',1,1),(2,'2025-12-22 09:12:03','2222222222','服务名称2','upload/obsidian-service-workshop.webp','★','非常满意','评价反馈2','2025-12-22 17:12:03','账号2','姓名2','维修账号2','维修姓名2',2,2),(3,'2025-12-22 09:12:03','3333333333','服务名称3','upload/obsidian-service-workshop.webp','★','非常满意','评价反馈3','2025-12-22 17:12:03','账号3','姓名3','维修账号3','维修姓名3',3,3),(4,'2025-12-22 09:12:03','4444444444','服务名称4','upload/obsidian-service-workshop.webp','★','非常满意','评价反馈4','2025-12-22 17:12:03','账号4','姓名4','维修账号4','维修姓名4',4,4),(5,'2025-12-22 09:12:03','5555555555','服务名称5','upload/obsidian-service-workshop.webp','★','非常满意','评价反馈5','2025-12-22 17:12:03','账号5','姓名5','维修账号5','维修姓名5',5,5),(6,'2025-12-22 09:12:03','6666666666','服务名称6','upload/obsidian-service-workshop.webp','★','非常满意','评价反馈6','2025-12-22 17:12:03','账号6','姓名6','维修账号6','维修姓名6',6,6),(7,'2025-12-22 09:12:03','7777777777','服务名称7','upload/obsidian-service-workshop.webp','★','非常满意','评价反馈7','2025-12-22 17:12:03','账号7','姓名7','维修账号7','维修姓名7',7,7),(8,'2025-12-22 09:12:03','8888888888','服务名称8','upload/obsidian-service-workshop.webp','★','非常满意','评价反馈8','2025-12-22 17:12:03','账号8','姓名8','维修账号8','维修姓名8',8,8),(9,'2025-12-22 09:21:47','1734340902281','第一服务AA','upload/obsidian-service-workshop.webp','★★★','满意','操作者可以在输入框输入   简介信息    等内容。','2025-12-22 17:21:42','11','张三','22','李四',1734340516997,9),(10,'2026-04-09 14:29:52','1775744983034','服务名称5','upload/obsidian-service-workshop.webp','★★','不满意','','2026-04-09 22:29:43','账号2','姓名2','维修账号5','维修姓名5',12,10),(11,'2026-04-09 14:30:13','1775745007461','服务名称2','upload/obsidian-service-workshop.webp','★★','满意','','2026-04-09 22:30:07','账号2','姓名2','维修账号2','维修姓名2',12,2),(12,'2026-04-13 11:57:05','1776081418696','服务名称1','upload/obsidian-service-workshop.webp','★★★★','满意','222','2026-04-13 19:56:58','账号1','姓名1','维修账号1','维修姓名1',11,11),(13,'2026-04-13 11:57:29','1776081441955','服务名称1','upload/obsidian-service-workshop.webp','★','不满意','99','2026-04-13 19:57:21','账号1','姓名1','维修账号1','维修姓名1',11,1),(14,'2026-04-18 01:13:26','1776474797248','服务名称1','upload/obsidian-service-workshop.webp','★★★','非常满意','11','2026-04-18 09:13:17','账号1','姓名1','维修账号1','维修姓名1',11,12),(15,'2026-05-07 07:15:22','1778138113045','服务名称1','upload/obsidian-service-workshop.webp','★★★★','非常满意','dadhah','2026-05-07 15:15:13','wkb','ddd','维修账号1','维修姓名1',1778137948419,14);
/*!40000 ALTER TABLE `pingjiafankui` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pinpaixinxi`
--

DROP TABLE IF EXISTS `pinpaixinxi`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pinpaixinxi` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `addtime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `pinpai` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '品牌',
  PRIMARY KEY (`id`),
  UNIQUE KEY `pinpai` (`pinpai`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='品牌信息';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pinpaixinxi`
--

LOCK TABLES `pinpaixinxi` WRITE;
/*!40000 ALTER TABLE `pinpaixinxi` DISABLE KEYS */;
INSERT INTO `pinpaixinxi` VALUES (1,'2025-12-22 09:12:03','品牌1'),(2,'2025-12-22 09:12:03','品牌2'),(3,'2025-12-22 09:12:03','品牌3'),(4,'2025-12-22 09:12:03','品牌4'),(5,'2025-12-22 09:12:03','品牌5'),(6,'2025-12-22 09:12:03','品牌6'),(7,'2025-12-22 09:12:03','品牌7'),(8,'2025-12-22 09:12:03','品牌8'),(9,'2025-12-22 09:16:13','小米');
/*!40000 ALTER TABLE `pinpaixinxi` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `shouhoufuwu`
--

DROP TABLE IF EXISTS `shouhoufuwu`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `shouhoufuwu` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `addtime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `fuwumingcheng` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '服务名称',
  `fuwufenlei` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '服务分类',
  `jiage` double DEFAULT NULL COMMENT '价格',
  `fengmian` longtext COLLATE utf8mb4_unicode_ci COMMENT '封面',
  `fabushijian` datetime DEFAULT NULL COMMENT '发布时间',
  `fuwuxiangqing` longtext COLLATE utf8mb4_unicode_ci COMMENT '服务详情',
  `weixiuzhanghao` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '维修账号',
  `weixiuxingming` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '维修姓名',
  `clicktime` datetime DEFAULT NULL COMMENT '最近点击时间',
  `clicknum` int(11) DEFAULT '0' COMMENT '点击次数',
  `storeupnum` int(11) DEFAULT '0' COMMENT '收藏数',
  PRIMARY KEY (`id`),
  KEY `fuwufenlei` (`fuwufenlei`),
  CONSTRAINT `shouhoufuwu_ibfk_1` FOREIGN KEY (`fuwufenlei`) REFERENCES `fuwufenlei` (`fuwufenlei`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='售后服务';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `shouhoufuwu`
--

LOCK TABLES `shouhoufuwu` WRITE;
/*!40000 ALTER TABLE `shouhoufuwu` DISABLE KEYS */;
INSERT INTO `shouhoufuwu` VALUES (1,'2025-12-22 09:12:03','服务名称1','服务分类1',1,'upload/obsidian-service-workshop.webp','2025-12-22 17:12:03','服务详情1','维修账号1','维修姓名1','2026-05-10 14:24:37',17,1),(2,'2025-12-22 09:12:03','服务名称2','服务分类2',2,'upload/obsidian-service-workshop.webp','2025-12-22 17:12:03','服务详情2','维修账号2','维修姓名2','2026-04-22 20:06:48',4,2),(3,'2025-12-22 09:12:03','服务名称3','服务分类3',3,'upload/obsidian-service-workshop.webp','2025-12-22 17:12:03','服务详情3','维修账号3','维修姓名3','2025-12-22 17:12:03',3,3),(4,'2025-12-22 09:12:03','服务名称4','服务分类4',4,'upload/obsidian-service-workshop.webp','2025-12-22 17:12:03','服务详情4','维修账号4','维修姓名4','2025-12-22 17:15:04',5,4),(5,'2025-12-22 09:12:03','服务名称5','服务分类5',5,'upload/obsidian-service-workshop.webp','2025-12-22 17:12:03','服务详情5','维修账号5','维修姓名5','2026-04-09 22:27:07',7,5),(6,'2025-12-22 09:12:03','服务名称6','服务分类6',6,'upload/obsidian-service-workshop.webp','2025-12-22 17:12:03','服务详情6','维修账号6','维修姓名6','2025-12-22 17:12:03',6,6),(7,'2025-12-22 09:12:03','服务名称7','服务分类7',99,'upload/obsidian-service-workshop.webp','2026-02-11 17:12:03','<p>服务详情7</p>','维修账号7','维修姓名7','2026-04-13 19:50:43',9,7),(8,'2025-12-22 09:12:03','服务名称8','维修',888,'upload/obsidian-service-workshop.webp','2026-01-15 17:12:03','<p>服务详情8</p>','维修账号8','维修姓名8','2026-04-09 22:24:25',9,8),(9,'2025-12-22 09:17:26','第一服务AA','维修',22,'upload/obsidian-service-workshop.webp','2025-12-22 17:16:56','<p>操作者可以在输入框输入&nbsp;&nbsp;详情信息&nbsp;&nbsp;等内容。</p><p>操作者可以在输入框输入&nbsp;&nbsp;详情信息&nbsp;&nbsp;等内容。</p><p>操作者可以在输入框输入&nbsp;&nbsp;详情信息&nbsp;&nbsp;等内容。</p><p>操作者可以在输入框输入&nbsp;&nbsp;详情信息&nbsp;&nbsp;等内容。</p><p><br></p><p>操作者可以在输入框输入&nbsp;&nbsp;详情信息&nbsp;&nbsp;等内容。</p><p><br></p><p>操作者可以在输入框输入&nbsp;&nbsp;详情信息&nbsp;&nbsp;等内容。</p><p>操作者可以在输入框输入&nbsp;&nbsp;详情信息&nbsp;&nbsp;等内容。</p><p>操作者可以在输入框输入&nbsp;&nbsp;详情信息&nbsp;&nbsp;等内容。</p><p>操作者可以在输入框输入&nbsp;&nbsp;详情信息&nbsp;&nbsp;等内容。</p><p>操作者可以在输入框输入&nbsp;&nbsp;详情信息&nbsp;&nbsp;等内容。</p><p>操作者可以在输入框输入&nbsp;&nbsp;详情信息&nbsp;&nbsp;等内容。</p><p>操作者可以在输入框输入&nbsp;&nbsp;详情信息&nbsp;&nbsp;等内容。</p><p><br></p>','22','李四','2026-04-18 09:05:51',7,0);
/*!40000 ALTER TABLE `shouhoufuwu` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `storeup`
--

DROP TABLE IF EXISTS `storeup`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `storeup` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `addtime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `userid` bigint(20) NOT NULL COMMENT '用户id',
  `refid` bigint(20) DEFAULT NULL COMMENT '商品id',
  `tablename` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '表名',
  `name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '名称',
  `picture` longtext COLLATE utf8mb4_unicode_ci COMMENT '图片',
  `type` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT '1' COMMENT '类型',
  `inteltype` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '推荐类型',
  `remark` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='收藏表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `storeup`
--

LOCK TABLES `storeup` WRITE;
/*!40000 ALTER TABLE `storeup` DISABLE KEYS */;
INSERT INTO `storeup` VALUES (1,'2025-12-22 09:19:44',1734340516997,9,'guzhangpaicha','第一故障AAA','upload/obsidian-engine-detail.webp','1',NULL,NULL);
/*!40000 ALTER TABLE `storeup` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `token`
--

DROP TABLE IF EXISTS `token`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `token` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `userid` bigint(20) NOT NULL COMMENT '用户id',
  `username` varchar(100) NOT NULL COMMENT '用户名',
  `tablename` varchar(100) DEFAULT NULL COMMENT '表名',
  `role` varchar(100) DEFAULT NULL COMMENT '角色',
  `token` varchar(200) NOT NULL COMMENT '密码',
  `addtime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '新增时间',
  `expiratedtime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '过期时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8 COMMENT='token表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `token`
--

LOCK TABLES `token` WRITE;
/*!40000 ALTER TABLE `token` DISABLE KEYS */;
INSERT INTO `token` VALUES (1,1734340516997,'11','chezhu','车主','unmh8vr22nycpzz29qzkslslm678wfrt','2025-12-22 09:15:20','2025-12-22 10:19:34'),(2,1,'admin','users','管理员','n26fy0juxmlzbdupi5h2x61jizuogugn','2025-12-22 09:15:35','2026-05-07 07:04:53'),(3,1734340561566,'22','weixiujishi','维修技师','onph6a357iic0erxxgpvug4gow07u844','2025-12-22 09:19:24','2025-12-22 10:19:24'),(4,11,'账号1','chezhu','车主','sqzhtyvwey7rvscfyjc4jto23aop2fit','2026-04-09 11:14:00','2026-05-10 07:22:53'),(5,12,'账号2','chezhu','车主','vl0l66v07rtiu6ehf13upaqcko6oqpxa','2026-04-09 14:26:32','2026-04-09 15:26:33'),(6,25,'维修账号5','weixiujishi','维修技师','ngjtu29vcjrx8od3ivif0t24jlvooj3d','2026-04-09 14:28:00','2026-04-09 15:28:01'),(7,21,'维修账号1','weixiujishi','维修技师','5cczbrxscpys73x5wykds97anpr7f23c','2026-04-13 11:55:15','2026-07-11 06:04:08'),(8,22,'维修账号2','weixiujishi','维修技师','v6xrfy65p7hk30gudvbgy28v7vxh3e3p','2026-04-22 12:07:01','2026-04-22 13:07:01'),(9,1778137948419,'wkb','chezhu','车主','ny4t58sy8qu4hpykqxc34yym8lxgm3na','2026-05-07 07:12:39','2026-05-07 08:12:40');
/*!40000 ALTER TABLE `token` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `users`
--

DROP TABLE IF EXISTS `users`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `users` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `username` varchar(100) NOT NULL COMMENT '用户名',
  `password` varchar(100) NOT NULL COMMENT '密码',
  `image` varchar(200) DEFAULT NULL COMMENT '头像',
  `role` varchar(100) DEFAULT '管理员' COMMENT '角色',
  `addtime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '新增时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8 COMMENT='管理员表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `users`
--

LOCK TABLES `users` WRITE;
/*!40000 ALTER TABLE `users` DISABLE KEYS */;
INSERT INTO `users` VALUES (1,'admin','admin','upload/obsidian-mechanic-portrait.webp','管理员','2025-12-22 09:12:04');
/*!40000 ALTER TABLE `users` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `weixiujilu`
--

DROP TABLE IF EXISTS `weixiujilu`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `weixiujilu` (
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
) ENGINE=InnoDB AUTO_INCREMENT=16 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='维修记录';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `weixiujilu`
--

LOCK TABLES `weixiujilu` WRITE;
/*!40000 ALTER TABLE `weixiujilu` DISABLE KEYS */;
INSERT INTO `weixiujilu` VALUES (1,'2025-12-22 09:12:03','1111111111','服务名称1','服务分类1','upload/obsidian-service-workshop.webp',1,'配件名称1',1,1,'2025-12-22 17:12:03','维修说明1','账号1','姓名1','手机1','车牌号1','维修账号1','维修姓名1','已支付'),(2,'2025-12-22 09:12:03','2222222222','服务名称2','服务分类2','upload/obsidian-service-workshop.webp',2,'配件名称2',2,2,'2025-12-22 17:12:03','维修说明2','账号2','姓名2','手机2','车牌号2','维修账号2','维修姓名2','已支付'),(3,'2025-12-22 09:12:03','3333333333','服务名称3','服务分类3','upload/obsidian-service-workshop.webp',3,'配件名称3',3,3,'2025-12-22 17:12:03','维修说明3','账号3','姓名3','手机3','车牌号3','维修账号3','维修姓名3','未支付'),(4,'2025-12-22 09:12:03','4444444444','服务名称4','服务分类4','upload/obsidian-service-workshop.webp',4,'配件名称4',4,4,'2025-12-22 17:12:03','维修说明4','账号4','姓名4','手机4','车牌号4','维修账号4','维修姓名4','未支付'),(5,'2025-12-22 09:12:03','5555555555','服务名称5','服务分类5','upload/obsidian-service-workshop.webp',5,'配件名称5',5,5,'2025-12-22 17:12:03','维修说明5','账号5','姓名5','手机5','车牌号5','维修账号5','维修姓名5','未支付'),(6,'2025-12-22 09:12:03','6666666666','服务名称6','服务分类6','upload/obsidian-service-workshop.webp',6,'配件名称6',6,6,'2025-12-22 17:12:03','维修说明6','账号6','姓名6','手机6','车牌号6','维修账号6','维修姓名6','未支付'),(7,'2025-12-22 09:12:03','7777777777','服务名称7','服务分类7','upload/obsidian-service-workshop.webp',7,'配件名称7',7,7,'2025-12-22 17:12:03','维修说明7','账号7','姓名7','手机7','车牌号7','维修账号7','维修姓名7','未支付'),(8,'2025-12-22 09:12:03','8888888888','服务名称8','服务分类8','upload/obsidian-service-workshop.webp',8,'配件名称8',8,8,'2025-12-22 17:12:03','维修说明8','账号8','姓名8','手机8','车牌号8','维修账号8','维修姓名8','未支付'),(9,'2025-12-22 09:21:16','1734340862476','第一服务AA','维修','upload/obsidian-service-workshop.webp',22,'第一配件,配件名称4,配件名称5',42,64,'2025-12-22 17:21:02','<p>操作者可以在输入框输入&nbsp;&nbsp;详情信息&nbsp;&nbsp;等内容。</p><p>操作者可以在输入框输入&nbsp;&nbsp;详情信息&nbsp;&nbsp;等内容。</p><p>操作者可以在输入框输入&nbsp;&nbsp;详情信息&nbsp;&nbsp;等内容。</p><p>操作者可以在输入框输入&nbsp;&nbsp;详情信息&nbsp;&nbsp;等内容。</p><p>操作者可以在输入框输入&nbsp;&nbsp;详情信息&nbsp;&nbsp;等内容。</p><p>操作者可以在输入框输入&nbsp;&nbsp;详情信息&nbsp;&nbsp;等内容。</p><p>操作者可以在输入框输入&nbsp;&nbsp;详情信息&nbsp;&nbsp;等内容。</p><p>操作者可以在输入框输入&nbsp;&nbsp;详情信息&nbsp;&nbsp;等内容。</p><p>操作者可以在输入框输入&nbsp;&nbsp;详情信息&nbsp;&nbsp;等内容。</p><p>操作者可以在输入框输入&nbsp;&nbsp;详情信息&nbsp;&nbsp;等内容。</p><p>操作者可以在输入框输入&nbsp;&nbsp;详情信息&nbsp;&nbsp;等内容。</p><p>操作者可以在输入框输入&nbsp;&nbsp;详情信息&nbsp;&nbsp;等内容。</p><p><br></p>','11','张三','15111111111','京W45125','22','李四','已支付'),(10,'2026-04-09 14:29:00','1775744926079','服务名称5','服务分类5','upload/obsidian-service-workshop.webp',5,'配件名称4,配件名称1,配件名称6,米其林轮胎',44,49,'2026-04-09 22:28:46','','账号2','姓名2','13823888882','1113','维修账号5','维修姓名5','已支付'),(11,'2026-04-13 11:55:51','1776081339588','服务名称1','服务分类1','upload/obsidian-service-workshop.webp',1,'配件名称2,配件名称4,配件名称5,配件名称6,配件名称8,米其林轮胎',58,59,'2026-04-13 19:55:39','','账号1','姓名1','13823888881','444','维修账号1','维修姓名1','已支付'),(12,'2026-04-18 01:12:39','1776474724438','服务名称1','服务分类1','upload/obsidian-service-workshop.webp',1,'配件名称2,配件名称3,配件名称4,配件名称5',14,15,'2026-04-18 09:12:04','','账号1','姓名1','13823888881','11','维修账号1','维修姓名1','已支付'),(13,'2026-04-22 11:44:40','1776858044143','服务名称1','服务分类1','upload/obsidian-service-workshop.webp',1,'',0,1,'2026-04-22 19:40:44','','账号1','姓名1','13823888881','666','维修账号1','维修姓名1','未支付'),(14,'2026-05-07 07:14:37','1778138056198','服务名称1','服务分类1','upload/obsidian-service-workshop.webp',1,'配件名称4,配件名称5',9,10,'2026-05-07 15:14:16','','wkb','ddd','15338361290','445455','维修账号1','维修姓名1','已支付'),(15,'2026-05-10 06:26:02','1778394311106','服务名称1','服务分类1','upload/obsidian-service-workshop.webp',1,'配件名称2',2,3,'2026-05-10 14:25:11','','账号1','姓名1','13823888881','879798','维修账号1','维修姓名1','未支付');
/*!40000 ALTER TABLE `weixiujilu` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `weixiujishi`
--

DROP TABLE IF EXISTS `weixiujishi`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `weixiujishi` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `addtime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `weixiuzhanghao` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '维修账号',
  `mima` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '密码',
  `weixiuxingming` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '维修姓名',
  `xingbie` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '性别',
  `lianxidianhua` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '联系电话',
  `touxiang` longtext COLLATE utf8mb4_unicode_ci COMMENT '头像',
  PRIMARY KEY (`id`),
  UNIQUE KEY `weixiuzhanghao` (`weixiuzhanghao`)
) ENGINE=InnoDB AUTO_INCREMENT=1734340561567 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='维修技师';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `weixiujishi`
--

LOCK TABLES `weixiujishi` WRITE;
/*!40000 ALTER TABLE `weixiujishi` DISABLE KEYS */;
INSERT INTO `weixiujishi` VALUES (21,'2025-12-22 09:12:03','维修账号1','123456','维修姓名1','男','13823888881','upload/obsidian-mechanic-portrait.webp'),(22,'2025-12-22 09:12:03','维修账号2','123456','维修姓名2','男','13823888882','upload/obsidian-mechanic-portrait.webp'),(23,'2025-12-22 09:12:03','维修账号3','123456','维修姓名3','男','13823888883','upload/obsidian-mechanic-portrait.webp'),(24,'2025-12-22 09:12:03','维修账号4','123456','维修姓名4','男','13823888884','upload/obsidian-mechanic-portrait.webp'),(25,'2025-12-22 09:12:03','维修账号5','123456','维修姓名5','男','13823888885','upload/obsidian-mechanic-portrait.webp'),(26,'2025-12-22 09:12:03','维修账号6','123456','维修姓名6','男','13823888886','upload/obsidian-mechanic-portrait.webp'),(27,'2025-12-22 09:12:03','维修账号7','123456','维修姓名7','男','13823888887','upload/obsidian-mechanic-portrait.webp'),(28,'2025-12-22 09:12:03','维修账号8','123456','维修姓名8','男','13823888888','upload/obsidian-mechanic-portrait.webp'),(1734340561566,'2025-12-22 09:16:01','22','22','李四','女','15118888888','upload/obsidian-mechanic-portrait.webp');
/*!40000 ALTER TABLE `weixiujishi` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `weixiuziliao`
--

DROP TABLE IF EXISTS `weixiuziliao`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `weixiuziliao` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `addtime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `ziliaomingcheng` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '资料名称',
  `ziliaowenjian` longtext COLLATE utf8mb4_unicode_ci COMMENT '资料文件',
  `fengmian` longtext COLLATE utf8mb4_unicode_ci COMMENT '封面',
  `shangchuanshijian` date DEFAULT NULL COMMENT '上传时间',
  `ziliaoneirong` longtext COLLATE utf8mb4_unicode_ci COMMENT '资料内容',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='维修资料';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `weixiuziliao`
--

LOCK TABLES `weixiuziliao` WRITE;
/*!40000 ALTER TABLE `weixiuziliao` DISABLE KEYS */;
INSERT INTO `weixiuziliao` VALUES (1,'2025-12-22 09:12:04','资料名称1','','upload/obsidian-engine-detail.webp','2025-12-22','资料内容1'),(2,'2025-12-22 09:12:04','资料名称2','','upload/obsidian-engine-detail.webp','2025-12-22','资料内容2'),(3,'2025-12-22 09:12:04','资料名称3','','upload/obsidian-engine-detail.webp','2025-12-22','资料内容3'),(4,'2025-12-22 09:12:04','资料名称4','','upload/obsidian-engine-detail.webp','2025-12-22','资料内容4'),(5,'2025-12-22 09:12:04','资料名称5','','upload/obsidian-engine-detail.webp','2025-12-22','资料内容5'),(6,'2025-12-22 09:12:04','资料名称6','','upload/obsidian-engine-detail.webp','2025-12-22','资料内容6'),(7,'2025-12-22 09:12:04','资料名称7','','upload/obsidian-engine-detail.webp','2025-12-22','资料内容7'),(8,'2025-12-22 09:12:04','资料名称8','','upload/obsidian-engine-detail.webp','2025-12-22','资料内容8'),(9,'2025-12-22 09:18:57','第一资料','upload/1734340728844.doc','upload/obsidian-engine-detail.webp','2025-12-22','<p>操作者可以在输入框输入&nbsp;&nbsp;详情信息&nbsp;&nbsp;等内容。</p><p>操作者可以在输入框输入&nbsp;&nbsp;详情信息&nbsp;&nbsp;等内容。</p><p>操作者可以在输入框输入&nbsp;&nbsp;详情信息&nbsp;&nbsp;等内容。</p><p>操作者可以在输入框输入&nbsp;&nbsp;详情信息&nbsp;&nbsp;等内容。</p><p><br></p><p>操作者可以在输入框输入&nbsp;&nbsp;详情信息&nbsp;&nbsp;等内容。</p><p><br></p><p>操作者可以在输入框输入&nbsp;&nbsp;详情信息&nbsp;&nbsp;等内容。</p><p>操作者可以在输入框输入&nbsp;&nbsp;详情信息&nbsp;&nbsp;等内容。</p><p>操作者可以在输入框输入&nbsp;&nbsp;详情信息&nbsp;&nbsp;等内容。</p><p>操作者可以在输入框输入&nbsp;&nbsp;详情信息&nbsp;&nbsp;等内容。</p><p>操作者可以在输入框输入&nbsp;&nbsp;详情信息&nbsp;&nbsp;等内容。</p><p>操作者可以在输入框输入&nbsp;&nbsp;详情信息&nbsp;&nbsp;等内容。</p><p>操作者可以在输入框输入&nbsp;&nbsp;详情信息&nbsp;&nbsp;等内容。</p><p><br></p>'),(10,'2026-04-17 13:47:04','222','upload/1776433560932.png','upload/obsidian-engine-detail.webp','2026-04-17','');
/*!40000 ALTER TABLE `weixiuziliao` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `xinnengyuanqiche`
--

DROP TABLE IF EXISTS `xinnengyuanqiche`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `xinnengyuanqiche` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `addtime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `qichexinghao` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '汽车型号',
  `qicheleixing` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '汽车类型',
  `pinpai` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '品牌',
  `baigonglijiasu` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '百公里加速',
  `zuigaoshisu` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '最高时速',
  `xuhanggonglishu` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '续航公里数',
  `xiaolv` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '效率',
  `jiage` double DEFAULT NULL COMMENT '价格',
  `zuoweishu` int(11) DEFAULT NULL COMMENT '座位数',
  `donglizongcheng` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '动力总成',
  `chongdianchatou` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '充电插头',
  `fengmian` longtext COLLATE utf8mb4_unicode_ci COMMENT '封面',
  `waixingshiyang` longtext COLLATE utf8mb4_unicode_ci COMMENT '外形式样',
  `chongdianfangan` longtext COLLATE utf8mb4_unicode_ci COMMENT '充电方案',
  `jishuguige` longtext COLLATE utf8mb4_unicode_ci COMMENT '技术规格',
  `xiangxijieshao` longtext COLLATE utf8mb4_unicode_ci COMMENT '详细介绍',
  `clicktime` datetime DEFAULT NULL COMMENT '最近点击时间',
  `clicknum` int(11) DEFAULT '0' COMMENT '点击次数',
  `storeupnum` int(11) DEFAULT '0' COMMENT '收藏数',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='新能源汽车';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `xinnengyuanqiche`
--

LOCK TABLES `xinnengyuanqiche` WRITE;
/*!40000 ALTER TABLE `xinnengyuanqiche` DISABLE KEYS */;
INSERT INTO `xinnengyuanqiche` VALUES (1,'2025-12-22 09:12:03','汽车型号1','汽车类型1','品牌1','百公里加速1','最高时速1','续航公里数1','效率1',1,1,'动力总成1','充电插头1','upload/obsidian-ev-charging.webp','外形式样1','充电方案1','技术规格1','详细介绍1','2025-12-22 17:12:03',1,1),(2,'2025-12-22 09:12:03','汽车型号2','汽车类型2','品牌2','百公里加速2','最高时速2','续航公里数2','效率2',2,2,'动力总成2','充电插头2','upload/obsidian-ev-charging.webp','外形式样2','充电方案2','技术规格2','详细介绍2','2025-12-22 17:12:03',2,2),(3,'2025-12-22 09:12:03','汽车型号3','汽车类型3','品牌3','百公里加速3','最高时速3','续航公里数3','效率3',3,3,'动力总成3','充电插头3','upload/obsidian-ev-charging.webp','外形式样3','充电方案3','技术规格3','详细介绍3','2025-12-22 17:20:20',6,3),(4,'2025-12-22 09:12:03','汽车型号4','汽车类型4','品牌4','百公里加速4','最高时速4','续航公里数4','效率4',4,4,'动力总成4','充电插头4','upload/obsidian-ev-charging.webp','外形式样4','充电方案4','技术规格4','详细介绍4','2025-12-22 17:12:03',4,4),(5,'2025-12-22 09:12:03','汽车型号5','汽车类型5','品牌5','百公里加速5','最高时速5','续航公里数5','效率5',5,5,'动力总成5','充电插头5','upload/obsidian-ev-charging.webp','外形式样5','充电方案5','技术规格5','详细介绍5','2025-12-22 17:12:03',5,5),(6,'2025-12-22 09:12:03','汽车型号6','汽车类型6','品牌6','百公里加速6','最高时速6','续航公里数6','效率6',6,6,'动力总成6','充电插头6','upload/obsidian-ev-charging.webp','外形式样6','充电方案6','技术规格6','详细介绍6','2025-12-22 17:12:03',6,6),(7,'2025-12-22 09:12:03','汽车型号7','汽车类型7','品牌7','百公里加速7','最高时速7','续航公里数7','效率7',7,7,'动力总成7','充电插头7','upload/obsidian-ev-charging.webp','外形式样7','充电方案7','技术规格7','详细介绍7','2026-04-13 19:54:18',8,7),(8,'2025-12-22 09:12:03','汽车型号8','汽车类型8','品牌8','百公里加速8','最高时速8','续航公里数8','效率8',8,8,'动力总成8','充电插头8','upload/obsidian-ev-charging.webp','外形式样8','充电方案8','技术规格8','详细介绍8','2025-12-22 17:12:03',8,8),(9,'2025-12-22 09:16:42','小米su7','小轿车','小米','5521','222','333','222',33,33,'33','333','upload/obsidian-ev-charging.webp','操作者可以在输入框输入   简介信息    等内容。','操作者可以在输入框输入   简介信息    等内容。','操作者可以在输入框输入   简介信息    等内容。','<p>操作者可以在输入框输入&nbsp;&nbsp;详情信息&nbsp;&nbsp;等内容。</p><p>操作者可以在输入框输入&nbsp;&nbsp;详情信息&nbsp;&nbsp;等内容。</p><p>操作者可以在输入框输入&nbsp;&nbsp;详情信息&nbsp;&nbsp;等内容。</p><p>操作者可以在输入框输入&nbsp;&nbsp;详情信息&nbsp;&nbsp;等内容。</p><p>操作者可以在输入框输入&nbsp;&nbsp;详情信息&nbsp;&nbsp;等内容。</p><p>操作者可以在输入框输入&nbsp;&nbsp;详情信息&nbsp;&nbsp;等内容。</p><p>操作者可以在输入框输入&nbsp;&nbsp;详情信息&nbsp;&nbsp;等内容。</p><p>操作者可以在输入框输入&nbsp;&nbsp;详情信息&nbsp;&nbsp;等内容。</p><p>操作者可以在输入框输入&nbsp;&nbsp;详情信息&nbsp;&nbsp;等内容。</p><p>操作者可以在输入框输入&nbsp;&nbsp;详情信息&nbsp;&nbsp;等内容。</p><p>操作者可以在输入框输入&nbsp;&nbsp;详情信息&nbsp;&nbsp;等内容。</p><p>操作者可以在输入框输入&nbsp;&nbsp;详情信息&nbsp;&nbsp;等内容。</p><p><br></p>','2026-04-13 19:58:09',2,0);
/*!40000 ALTER TABLE `xinnengyuanqiche` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-07-11 14:34:53
