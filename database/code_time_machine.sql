/*
 Navicat Premium Dump SQL

 Source Server         : mysql
 Source Server Type    : MySQL
 Source Server Version : 80026 (8.0.26)
 Source Host           : 127.0.0.1:3306
 Source Schema         : code_time_machine

 Target Server Type    : MySQL
 Target Server Version : 80026 (8.0.26)
 File Encoding         : 65001

 Date: 31/12/2025 19:17:52
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for ai_analysis
-- ----------------------------
DROP TABLE IF EXISTS `ai_analysis`;
CREATE TABLE `ai_analysis`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `commit_id` bigint NOT NULL COMMENT '提交ID',
  `repo_id` bigint NOT NULL COMMENT '仓库ID',
  `analysis_type` enum('COMMIT','FILE','QUESTION') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'COMMIT' COMMENT '分析类型',
  `summary` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '改动摘要',
  `purpose` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '改动目的',
  `impact` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '影响分析',
  `technical_details` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '技术细节',
  `suggestions` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT 'AI建议',
  `change_category` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '变更分类(feature/bugfix/refactor/docs等)',
  `complexity_score` int NULL DEFAULT NULL COMMENT '复杂度评分(1-10)',
  `importance_score` int NULL DEFAULT NULL COMMENT '重要性评分(1-10)',
  `prompt_hash` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'Prompt哈希(用于缓存)',
  `model_used` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '使用的AI模型',
  `tokens_used` int NULL DEFAULT NULL COMMENT '消耗的Token数',
  `response_time` int NULL DEFAULT NULL COMMENT '响应时间(毫秒)',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_commit_type`(`commit_id` ASC, `analysis_type` ASC) USING BTREE,
  INDEX `idx_repo_id`(`repo_id` ASC) USING BTREE,
  INDEX `idx_change_category`(`change_category` ASC) USING BTREE,
  INDEX `idx_prompt_hash`(`prompt_hash` ASC) USING BTREE,
  CONSTRAINT `ai_analysis_ibfk_1` FOREIGN KEY (`commit_id`) REFERENCES `commit_record` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `ai_analysis_ibfk_2` FOREIGN KEY (`repo_id`) REFERENCES `repository` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 17 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'AI分析结果表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for chat_history
-- ----------------------------
DROP TABLE IF EXISTS `chat_history`;
CREATE TABLE `chat_history`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `session_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '会话ID',
  `repo_id` bigint NULL DEFAULT NULL COMMENT '关联仓库ID',
  `commit_id` bigint NULL DEFAULT NULL COMMENT '关联提交ID',
  `file_path` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '关联文件路径',
  `role` enum('user','assistant','system') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '角色',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '消息内容',
  `tokens_used` int NULL DEFAULT NULL COMMENT '消耗Token数',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_session`(`session_id` ASC) USING BTREE,
  INDEX `idx_repo_id`(`repo_id` ASC) USING BTREE,
  INDEX `idx_commit_id`(`commit_id` ASC) USING BTREE,
  INDEX `idx_created_at`(`created_at` ASC) USING BTREE,
  CONSTRAINT `chat_history_ibfk_1` FOREIGN KEY (`repo_id`) REFERENCES `repository` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT,
  CONSTRAINT `chat_history_ibfk_2` FOREIGN KEY (`commit_id`) REFERENCES `commit_record` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 99 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '对话记录表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for commit_record
-- ----------------------------
DROP TABLE IF EXISTS `commit_record`;
CREATE TABLE `commit_record`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `repo_id` bigint NOT NULL COMMENT '仓库ID',
  `commit_hash` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Commit SHA哈希值',
  `short_hash` varchar(7) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '短哈希值',
  `parent_hash` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '父Commit哈希',
  `author_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '作者名称',
  `author_email` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '作者邮箱',
  `commit_message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '提交信息',
  `commit_time` datetime NOT NULL COMMENT '提交时间',
  `additions` int NULL DEFAULT 0 COMMENT '新增行数',
  `deletions` int NULL DEFAULT 0 COMMENT '删除行数',
  `files_changed` int NULL DEFAULT 0 COMMENT '变更文件数',
  `is_merge` tinyint NULL DEFAULT 0 COMMENT '是否是Merge提交',
  `commit_order` int NULL DEFAULT NULL COMMENT '提交顺序(从早到晚)',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_repo_hash`(`repo_id` ASC, `commit_hash` ASC) USING BTREE,
  INDEX `idx_repo_time`(`repo_id` ASC, `commit_time` ASC) USING BTREE,
  INDEX `idx_repo_order`(`repo_id` ASC, `commit_order` ASC) USING BTREE,
  INDEX `idx_author`(`author_name` ASC) USING BTREE,
  INDEX `idx_commit_record_repo_time`(`repo_id` ASC, `commit_time` ASC) USING BTREE,
  INDEX `idx_commit_record_repo_order`(`repo_id` ASC, `commit_order` ASC) USING BTREE,
  INDEX `idx_commit_record_repo_hash`(`repo_id` ASC, `commit_hash` ASC) USING BTREE,
  CONSTRAINT `commit_record_ibfk_1` FOREIGN KEY (`repo_id`) REFERENCES `repository` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 7210 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '提交记录表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for file_change
-- ----------------------------
DROP TABLE IF EXISTS `file_change`;
CREATE TABLE `file_change`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `commit_id` bigint NOT NULL COMMENT '提交ID',
  `repo_id` bigint NOT NULL COMMENT '仓库ID',
  `file_path` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文件路径',
  `file_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文件名',
  `file_extension` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '文件扩展名',
  `change_type` enum('ADD','MODIFY','DELETE','RENAME','COPY') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '变更类型',
  `old_path` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '原路径(重命名时)',
  `additions` int NULL DEFAULT 0 COMMENT '新增行数',
  `deletions` int NULL DEFAULT 0 COMMENT '删除行数',
  `diff_text` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT 'Diff内容',
  `file_content` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '文件完整内容(用于展示)',
  `content_hash` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '内容哈希(用于去重)',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_commit_id`(`commit_id` ASC) USING BTREE,
  INDEX `idx_repo_file`(`repo_id` ASC, `file_path`(255) ASC) USING BTREE,
  INDEX `idx_extension`(`file_extension` ASC) USING BTREE,
  INDEX `idx_change_type`(`change_type` ASC) USING BTREE,
  INDEX `idx_file_change_repo_extension`(`repo_id` ASC, `file_extension` ASC) USING BTREE,
  INDEX `idx_file_change_repo_change_type`(`repo_id` ASC, `change_type` ASC) USING BTREE,
  INDEX `idx_file_change_repo_path`(`repo_id` ASC, `file_path` ASC) USING BTREE,
  INDEX `idx_file_change_commit_id`(`commit_id` ASC) USING BTREE,
  CONSTRAINT `file_change_ibfk_1` FOREIGN KEY (`commit_id`) REFERENCES `commit_record` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `file_change_ibfk_2` FOREIGN KEY (`repo_id`) REFERENCES `repository` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 52614 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '文件变更表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for file_snapshot
-- ----------------------------
DROP TABLE IF EXISTS `file_snapshot`;
CREATE TABLE `file_snapshot`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `repo_id` bigint NOT NULL COMMENT '仓库ID',
  `commit_id` bigint NOT NULL COMMENT '提交ID',
  `file_path` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文件路径',
  `content` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '文件内容',
  `line_count` int NULL DEFAULT 0 COMMENT '行数',
  `char_count` int NULL DEFAULT 0 COMMENT '字符数',
  `language` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '编程语言',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_commit_file`(`commit_id` ASC, `file_path`(255) ASC) USING BTREE,
  INDEX `idx_repo_file`(`repo_id` ASC, `file_path`(255) ASC) USING BTREE,
  CONSTRAINT `file_snapshot_ibfk_1` FOREIGN KEY (`repo_id`) REFERENCES `repository` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `file_snapshot_ibfk_2` FOREIGN KEY (`commit_id`) REFERENCES `commit_record` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '文件快照表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for repository
-- ----------------------------
DROP TABLE IF EXISTS `repository`;
CREATE TABLE `repository`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '仓库名称',
  `url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Git仓库URL',
  `local_path` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '本地克隆路径',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '仓库描述',
  `default_branch` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'main' COMMENT '默认分支',
  `language` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '主要编程语言',
  `stars` int NULL DEFAULT 0 COMMENT 'GitHub Stars数量',
  `total_commits` int NULL DEFAULT 0 COMMENT '总提交数',
  `total_files` int NULL DEFAULT 0 COMMENT '文件总数',
  `repo_size` bigint NULL DEFAULT 0 COMMENT '仓库大小(字节)',
  `status` tinyint NULL DEFAULT 0 COMMENT '状态: 0-待分析 1-分析中 2-分析完成 3-分析失败',
  `analyze_progress` int NULL DEFAULT 0 COMMENT '分析进度(0-100)',
  `last_analyzed_at` datetime NULL DEFAULT NULL COMMENT '最后分析时间',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `analyze_depth` int NULL DEFAULT NULL COMMENT '分析深度（提交数量限制）-1表示全部',
  `analyze_since` datetime NULL DEFAULT NULL COMMENT '分析起始时间',
  `analyze_path_filters` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '分析路径过滤（JSON数组格式）',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_url`(`url` ASC) USING BTREE,
  INDEX `idx_status`(`status` ASC) USING BTREE,
  INDEX `idx_created_at`(`created_at` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 46 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '仓库信息表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for statistics
-- ----------------------------
DROP TABLE IF EXISTS `statistics`;
CREATE TABLE `statistics`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `repo_id` bigint NOT NULL COMMENT '仓库ID',
  `stat_date` date NULL DEFAULT NULL COMMENT '统计日期',
  `stat_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '统计类型',
  `stat_key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '统计键',
  `stat_value` bigint NULL DEFAULT 0 COMMENT '统计值',
  `extra_data` json NULL COMMENT '额外数据',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_repo_type_key_date`(`repo_id` ASC, `stat_type` ASC, `stat_key` ASC, `stat_date` ASC) USING BTREE,
  INDEX `idx_repo_type`(`repo_id` ASC, `stat_type` ASC) USING BTREE,
  CONSTRAINT `statistics_ibfk_1` FOREIGN KEY (`repo_id`) REFERENCES `repository` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 18 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '统计汇总表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for system_config
-- ----------------------------
DROP TABLE IF EXISTS `system_config`;
CREATE TABLE `system_config`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `config_key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '配置键',
  `config_value` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '配置值',
  `config_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'STRING' COMMENT '配置类型',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '配置描述',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_config_key`(`config_key` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 6 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '系统配置表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for user_favorite
-- ----------------------------
DROP TABLE IF EXISTS `user_favorite`;
CREATE TABLE `user_favorite`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '用户ID(可选,匿名用cookie)',
  `favorite_type` enum('REPO','COMMIT','FILE') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '收藏类型',
  `target_id` bigint NOT NULL COMMENT '目标ID',
  `note` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_type_target`(`user_id` ASC, `favorite_type` ASC, `target_id` ASC) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户收藏表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- View structure for v_commit_overview
-- ----------------------------
DROP VIEW IF EXISTS `v_commit_overview`;
CREATE ALGORITHM = UNDEFINED SQL SECURITY DEFINER VIEW `v_commit_overview` AS select `r`.`id` AS `repo_id`,`r`.`name` AS `repo_name`,count(distinct `cr`.`id`) AS `total_commits`,count(distinct `cr`.`author_name`) AS `total_authors`,sum(`cr`.`additions`) AS `total_additions`,sum(`cr`.`deletions`) AS `total_deletions`,min(`cr`.`commit_time`) AS `first_commit`,max(`cr`.`commit_time`) AS `last_commit` from (`repository` `r` left join `commit_record` `cr` on((`r`.`id` = `cr`.`repo_id`))) group by `r`.`id`,`r`.`name`;

-- ----------------------------
-- View structure for v_file_timeline
-- ----------------------------
DROP VIEW IF EXISTS `v_file_timeline`;
CREATE ALGORITHM = UNDEFINED SQL SECURITY DEFINER VIEW `v_file_timeline` AS select `fc`.`repo_id` AS `repo_id`,`fc`.`file_path` AS `file_path`,`fc`.`file_name` AS `file_name`,`cr`.`commit_hash` AS `commit_hash`,`cr`.`short_hash` AS `short_hash`,`cr`.`commit_message` AS `commit_message`,`cr`.`author_name` AS `author_name`,`cr`.`commit_time` AS `commit_time`,`cr`.`commit_order` AS `commit_order`,`fc`.`change_type` AS `change_type`,`fc`.`additions` AS `additions`,`fc`.`deletions` AS `deletions`,`aa`.`summary` AS `ai_summary`,`aa`.`change_category` AS `change_category` from ((`file_change` `fc` join `commit_record` `cr` on((`fc`.`commit_id` = `cr`.`id`))) left join `ai_analysis` `aa` on((`aa`.`commit_id` = `cr`.`id`))) order by `fc`.`repo_id`,`fc`.`file_path`,`cr`.`commit_order`;

SET FOREIGN_KEY_CHECKS = 1;
