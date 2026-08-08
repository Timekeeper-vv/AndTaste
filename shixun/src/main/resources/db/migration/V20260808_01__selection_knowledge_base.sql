-- Structured selection knowledge base sourced from the 2023 "之间味道" handbook.
-- User-facing prices and lead times are references only until an operator reviews
-- and publishes a current version. Supplier costs, MOQ and margin data are not
-- stored in this consumer-facing catalog.
CREATE TABLE IF NOT EXISTS selection_category (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    category_key VARCHAR(60) NOT NULL UNIQUE,
    name VARCHAR(80) NOT NULL,
    description VARCHAR(300) NOT NULL,
    source_version VARCHAR(40) NOT NULL DEFAULT '2023',
    source_name VARCHAR(200) NOT NULL DEFAULT '选品手册-之间味道.pdf',
    review_status VARCHAR(30) NOT NULL DEFAULT 'pending_review',
    effective_from DATE NULL,
    reviewed_at DATETIME NULL,
    enabled TINYINT NOT NULL DEFAULT 1,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_selection_category_status (enabled, review_status, sort_order)
) COMMENT='文创选品知识库品类';

CREATE TABLE IF NOT EXISTS selection_option (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    option_key VARCHAR(80) NOT NULL UNIQUE,
    category_key VARCHAR(60) NOT NULL,
    name VARCHAR(120) NOT NULL,
    subtitle VARCHAR(200) NOT NULL,
    description VARCHAR(500) NOT NULL,
    material VARCHAR(500) NOT NULL,
    process VARCHAR(1000) NOT NULL,
    specification VARCHAR(500) NOT NULL,
    sample_lead_time VARCHAR(60) NOT NULL,
    bulk_lead_time VARCHAR(60) NOT NULL,
    retail_min DECIMAL(12,2) NULL,
    retail_max DECIMAL(12,2) NULL,
    retail_display VARCHAR(80) NOT NULL,
    tags VARCHAR(1000) NOT NULL,
    audience_tags VARCHAR(500) NOT NULL,
    occasion_tags VARCHAR(500) NOT NULL,
    budget_band VARCHAR(30) NOT NULL,
    cover_image_url VARCHAR(500) NULL,
    image_source VARCHAR(500) NULL,
    image_rights_status VARCHAR(40) NOT NULL DEFAULT 'pending_review',
    source_version VARCHAR(40) NOT NULL DEFAULT '2023',
    source_name VARCHAR(200) NOT NULL DEFAULT '选品手册-之间味道.pdf',
    source_page INT NULL,
    review_status VARCHAR(30) NOT NULL DEFAULT 'pending_review',
    effective_from DATE NULL,
    reviewed_at DATETIME NULL,
    enabled TINYINT NOT NULL DEFAULT 1,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_selection_option_filter (category_key, enabled, review_status, sort_order),
    INDEX idx_selection_option_budget (retail_min, retail_max),
    INDEX idx_selection_option_version (source_version, effective_from)
) COMMENT='文创选品知识库公开选项';

CREATE TABLE IF NOT EXISTS user_selection_favorite (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    option_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_selection_favorite (user_id, option_id),
    INDEX idx_selection_favorite_user (user_id)
) COMMENT='用户收藏的选品方向';

CREATE TABLE IF NOT EXISTS creative_selection_recommendation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    recommendation_no VARCHAR(80) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    asset_id BIGINT NULL,
    input_json JSON NOT NULL,
    option_ids_json JSON NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_selection_recommendation_user (user_id, created_at),
    INDEX idx_selection_recommendation_asset (asset_id)
) COMMENT='用户选品推荐记录，可关联 AI 创作作品';

CREATE TABLE IF NOT EXISTS selection_demand_request (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    request_no VARCHAR(80) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    option_id BIGINT NOT NULL,
    asset_id BIGINT NULL,
    theme VARCHAR(300) NULL,
    budget_max DECIMAL(12,2) NULL,
    audience VARCHAR(200) NULL,
    occasion VARCHAR(100) NULL,
    note VARCHAR(1000) NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'new',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_selection_demand_user (user_id, created_at),
    INDEX idx_selection_demand_status (status, created_at)
) COMMENT='用户商品化需求单';

INSERT INTO selection_category (category_key,name,description,source_version,source_name,review_status,effective_from,sort_order) VALUES
('food','食品','适合节日礼赠、地方风味和可分享的文化主题。','2023','选品手册-之间味道.pdf','approved','2023-01-01',10),
('stationery','文具','适合低客单、轻量化、快速试销和系列化传播。','2023','选品手册-之间味道.pdf','approved','2023-01-01',20),
('daily','日用','将文化视觉带入日常使用场景，适合礼赠和复购。','2023','选品手册-之间味道.pdf','approved','2023-01-01',30),
('toy','玩具','适合角色化 IP、亲子互动和收藏型产品。','2023','选品手册-之间味道.pdf','approved','2023-01-01',40),
('tableware','餐具','适合器物文化、节日礼赠和生活方式场景。','2023','选品手册-之间味道.pdf','approved','2023-01-01',50),
('souvenir','纪念品','适合景区、博物馆和城市文化的即时购买。','2023','选品手册-之间味道.pdf','approved','2023-01-01',60),
('accessory','饰品','适合将纹样、器物局部和吉祥寓意转成随身佩戴。','2023','选品手册-之间味道.pdf','approved','2023-01-01',70),
('apparel','服饰','适合系列化视觉、节日活动和城市文化传播。','2023','选品手册-之间味道.pdf','approved','2023-01-01',80),
('craft','工艺品','适合高文化含量、定制化和展陈礼赠方向。','2023','选品手册-之间味道.pdf','approved','2023-01-01',90),
('precious','贵金属','适合高客单收藏和纪念场景，必须进行正式报价与资质确认。','2023','选品手册-之间味道.pdf','approved','2023-01-01',100)
ON DUPLICATE KEY UPDATE name=VALUES(name),description=VALUES(description),review_status=VALUES(review_status),effective_from=VALUES(effective_from),updated_at=CURRENT_TIMESTAMP;

INSERT INTO selection_option (option_key,category_key,name,subtitle,description,material,process,specification,sample_lead_time,bulk_lead_time,retail_min,retail_max,retail_display,tags,audience_tags,occasion_tags,budget_band,cover_image_url,image_source,image_rights_status,source_version,source_name,source_page,review_status,effective_from,sort_order) VALUES
('food-cookie','food','曲奇糕点','轻量礼赠 · 低门槛试销','适合把馆藏纹样、地方故事和节日祝福转成可分享的礼盒。','食品级原料','多色/双色','200g/套','10-15天','15-25天',48,68,'48-68元','节日,礼赠,低客单,食品','年轻游客,家庭,送礼人群','节日,伴手礼,活动', '30-100','/selection/manual-food.jpg','选品手册-之间味道.pdf 第5页','pending_review','2023','选品手册-之间味道.pdf',5,'approved','2023-01-01',10),
('food-mooncake','food','文创月饼','节日限定 · 高礼赠感','适合节日 IP 联名和馆藏纹样礼盒，但需要提前确认食品资质、包装和保质期。','桃山皮/酥皮/传统','翻模/食品包装定制','50g*8块','10-15天','20-25天',258,258,'258元','节日,礼盒,高客单,食品','礼赠人群,企业客户,收藏者','中秋,节庆,团购','200+','/selection/manual-food.jpg','选品手册-之间味道.pdf 第5页','pending_review','2023','选品手册-之间味道.pdf',5,'approved','2023-01-01',20),
('food-coffee','food','挂耳/冻干咖啡','日常消耗 · 文化包装','适合将地方文化、博物馆色彩融入日常饮品和办公室礼赠。','咖啡粉/挂耳包材','挂耳/冻干咖啡粉','10g/袋，5袋','10-15天','25-30天',40,40,'约40元','日用,礼赠,轻量化,食品','年轻人,办公人群,游客','伴手礼,节日,日常','30-100','/selection/manual-food.jpg','选品手册-之间味道.pdf 第6页','pending_review','2023','选品手册-之间味道.pdf',6,'approved','2023-01-01',30),
('stationery-metal-bookmark','stationery','金属书签','文化符号 · 高辨识','适合将纹样、器物轮廓或地标局部压缩成轻量纪念品。','金属','烤漆/仿珐琅/印刷/腐蚀/电镀','2-8cm/个','7-15天','20-25天',15,40,'15-40元','低客单,轻量化,纪念,礼赠','年轻游客,文博爱好者,送礼人群','伴手礼,节日,日常','0-50','/selection/manual-stationery.jpg','选品手册-之间味道.pdf 第12页','pending_review','2023','选品手册-之间味道.pdf',12,'approved','2023-01-01',40),
('stationery-postcard','stationery','明信片套装','快速打样 · 适合内容传播','适合将一组文化元素做成系列卡面，也方便内容拍摄和线下打卡。','白卡纸/特种纸/亚克力','烫金/印刷/UV/凸版印刷','单张/套装','7-10天','15-25天',15,40,'15-40元','低客单,轻量化,系列化,打卡','年轻游客,亲子,文博爱好者','旅行,节日,打卡','0-50','/selection/manual-stationery.jpg','选品手册-之间味道.pdf 第21页','pending_review','2023','选品手册-之间味道.pdf',21,'approved','2023-01-01',50),
('stationery-sticker','stationery','主题贴纸包','低成本试爆款 · 易分享','适合将主视觉拆成多个角色、纹样和祝福元素，先测试用户偏好。','合成纸/PVC/特种纸/滴胶','印刷/烫金银/四色印刷','常规/套','7-15天','15-20天',10,45,'10-45元','低客单,低成本,系列化,社交传播','年轻人,亲子,学生','节日,打卡,日常','0-50','/selection/manual-stationery.jpg','选品手册-之间味道.pdf 第23页','pending_review','2023','选品手册-之间味道.pdf',23,'approved','2023-01-01',60),
('stationery-notebook','stationery','文化主题本册','日常使用 · 礼赠稳定','适合把文化故事延伸到封面、内页和包装，形成更完整的主题礼赠。','纸张/特种纸','印刷/烫金/压凹/UV','A5/A6','10-15天','15-20天',30,60,'30-60元','礼赠,日用,文具,系列化','学生,办公人群,文博爱好者','节日,开学,日常','0-100','/selection/manual-stationery.jpg','选品手册-之间味道.pdf 第14页','pending_review','2023','选品手册-之间味道.pdf',14,'approved','2023-01-01',70),
('daily-cushion','daily','异形抱枕','角色化 · 亲子互动','适合把守护兽、神话角色或地方吉祥物做成可拥抱的日用产品。','布艺/填充物','热转/数码彩喷/丝网/刺绣','随型','7-10天','15-20天',75,85,'75-85元','亲子,角色化,礼赠,日用','亲子,年轻人,家庭','节日,生日,家居','50-100','/selection/manual-daily.jpg','选品手册-之间味道.pdf 第37页','pending_review','2023','选品手册-之间味道.pdf',37,'approved','2023-01-01',80),
('daily-umbrella','daily','折叠雨伞','高频日用 · 城市传播','适合把城市色彩、地标和连续纹样延伸到通勤场景。','涤纶/金属骨架','丝网/UV/数码印刷','直径96cm','5-10天','15-20天',89,99,'89-99元','日用,城市文化,礼赠','年轻人,通勤人群,游客','节日,旅行,日常','50-100','/selection/manual-daily.jpg','选品手册-之间味道.pdf 第41页','pending_review','2023','选品手册-之间味道.pdf',41,'approved','2023-01-01',90),
('daily-towel','daily','文化主题毛巾','亲肤日用 · 低门槛礼赠','适合将地方色彩和简化纹样放入高频日用品，适合礼盒组合。','棉/纤维','彩喷/提花','20*70cm 或 30*60cm','3-10天','10-20天',39,69,'39-69元','日用,礼赠,低客单','家庭,年轻人,游客','节日,伴手礼,日常','30-100','/selection/manual-daily.jpg','选品手册-之间味道.pdf 第39页','pending_review','2023','选品手册-之间味道.pdf',39,'approved','2023-01-01',100),
('toy-pvc-figure','toy','PVC 潮玩公仔','角色 IP · 系列化收藏','适合把文化原型转成现代角色，后续可延展盲盒、挂件和场景系列。','PVC','注塑/喷漆/转印','8-9cm、8-13cm 或 23cm','20-25天','30-40天',69,129,'69-129元','角色化,收藏,系列化,潮玩','年轻人,亲子,收藏者','节日,展览,礼赠','50-200','/selection/manual-toy.jpg','选品手册-之间味道.pdf 第43页','pending_review','2023','选品手册-之间味道.pdf',43,'approved','2023-01-01',110),
('toy-plush-figure','toy','毛绒公仔','亲子友好 · 情绪价值','适合将地方传说、守护兽和吉祥物转成更柔软的陪伴型产品。','水晶超柔/布艺/毛毡','填充/钩针','8-9cm、8-13cm 或 23cm','10-20天','20-25天',69,129,'69-129元','亲子,角色化,礼赠,收藏','亲子,年轻人,家庭','节日,生日,展览','50-200','/selection/manual-toy.jpg','选品手册-之间味道.pdf 第45页','pending_review','2023','选品手册-之间味道.pdf',45,'approved','2023-01-01',120),
('tableware-ceramic-mug','tableware','陶瓷马克杯','器物文化 · 稳定礼赠','适合将纹样、色彩和器物轮廓带入日常饮用场景，适合做系列。','陶瓷/骨瓷','翻铸/花纸/釉上彩/刻胎/堆泥','8-16cm','14-18天','15-20天',35,80,'35-80元','日用,礼赠,器物,系列化','办公人群,年轻人,文博爱好者','节日,日常,伴手礼','30-100','/selection/manual-tableware.jpg','选品手册-之间味道.pdf 第28页','pending_review','2023','选品手册-之间味道.pdf',28,'approved','2023-01-01',130),
('tableware-tumbler','tableware','保温杯/随行杯','通勤日用 · 高复用','适合把主题视觉延伸到通勤用品，但需要在正式打样时确认杯型和贴花耐久。','PC+塑胶/不锈钢/不锈钢+塑胶','贴花/激光/合金件粘贴','参考产品册','14-18天','15-25天',50,90,'50-90元','日用,通勤,礼赠,城市文化','办公人群,年轻人,游客','节日,日常,伴手礼','30-100','/selection/manual-tableware.jpg','选品手册-之间味道.pdf 第32页','pending_review','2023','选品手册-之间味道.pdf',32,'approved','2023-01-01',140),
('tableware-coaster','tableware','主题杯垫','低客单 · 易做套装','适合将单个文化符号做成一组颜色和纹样，适合与杯子、茶礼组合。','硅胶/皮革/吸水石/亚克力/木质','雕刻/印刷/激光/贴花/流沙','8-12cm 或按日用尺寸','7-14天','15-20天',10,30,'10-30元','低客单,套装,礼赠,日用','年轻人,办公人群,游客','节日,伴手礼,日常','0-50','/selection/manual-tableware.jpg','选品手册-之间味道.pdf 第33页','pending_review','2023','选品手册-之间味道.pdf',33,'approved','2023-01-01',150),
('souvenir-alloy-magnet','souvenir','合金冰箱贴','首期优先 · 景区博物馆友好','适合将文物轮廓、地标或守护兽做成一眼可识别的纪念品。','合金','压铸/造旧/冷珐琅/烤漆/印刷','4-8cm','7-14天','15-20天',35,50,'35-50元','博物馆,景区,纪念,低客单','年轻游客,文博爱好者,亲子','旅行,打卡,伴手礼','30-100','/selection/manual-souvenir.jpg','选品手册-之间味道.pdf 第56页','pending_review','2023','选品手册-之间味道.pdf',56,'approved','2023-01-01',160),
('souvenir-zinc-badge','souvenir','锌合金徽章','低门槛收藏 · 可系列化','适合把一个文化主题拆成不同角色、纹样或等级，便于形成系列收藏。','锌合金','压铸/造旧/冷珐琅/烤漆/印刷','4-8cm','7-14天','15-20天',30,40,'30-40元','博物馆,系列化,纪念,低客单','年轻人,学生,收藏者','展览,打卡,伴手礼','0-50','/selection/manual-souvenir.jpg','选品手册-之间味道.pdf 第57页','pending_review','2023','选品手册-之间味道.pdf',57,'approved','2023-01-01',170),
('souvenir-alloy-keychain','souvenir','合金钥匙扣','随身携带 · 传播友好','适合把文化符号变成可随身使用的纪念品，兼顾展示和日用。','合金','压铸/造旧/冷珐琅/烤漆/彩喷','4-8cm','7-15天','15-20天',35,60,'35-60元','纪念,日用,低客单,城市文化','年轻游客,年轻人,文博爱好者','旅行,打卡,伴手礼','30-100','/selection/manual-souvenir.jpg','选品手册-之间味道.pdf 第59页','pending_review','2023','选品手册-之间味道.pdf',59,'approved','2023-01-01',180),
('accessory-pendant','accessory','文化吊坠','符号佩戴 · 高辨识','适合将器物局部、纹样和吉祥寓意转成随身佩戴的首饰方向。','金属/合金','油压/压铸/翻铸/镶嵌/镜砂/彩喷','按规格','13-18天','约15天',68,168,'按规格报价','饰品,礼赠,高辨识,文化符号','年轻人,礼赠人群,收藏者','节日,纪念,日常','50-200','/selection/manual-accessory.jpg','选品手册-之间味道.pdf 第65页','pending_review','2023','选品手册-之间味道.pdf',65,'approved','2023-01-01',190),
('apparel-canvas-bag','apparel','帆布单肩包','城市传播 · 日常使用','适合将完整主视觉放大到包面，形成移动传播和实用礼赠。','帆布','丝网/刺绣/数码/柯式/发泡/植绒','常规','5-7天','10-15天',99,149,'99-149元','城市文化,日用,礼赠,传播','年轻人,游客,办公人群','旅行,节日,日常','50-200','/selection/manual-stationery.jpg','选品手册-之间味道.pdf 第26页','pending_review','2023','选品手册-之间味道.pdf',26,'approved','2023-01-01',200),
('apparel-tshirt','apparel','文化主题 T 恤','视觉传播 · 系列化','适合将纹样、角色和城市故事做成系列服饰，但需要确认版权和印花耐久。','棉/混纺','丝网/拔印/刺绣/数码/柯式/发泡/植绒','短袖常规尺码','7-10天','15-20天',99,169,'99-169元','服饰,系列化,城市文化,传播','年轻人,游客,社群','节日,活动,日常','50-200','/selection/manual-stationery.jpg','选品手册-之间味道.pdf 第48页','pending_review','2023','选品手册-之间味道.pdf',48,'approved','2023-01-01',210),
('craft-ceramic','craft','定制陶瓷工艺品','高文化含量 · 定制方向','适合高完成度器物、展陈礼赠和博物馆合作项目，必须先做正式工艺确认。','陶瓷','釉/化妆土/釉上彩/刻胎','定制','20-30天','约15天',NULL,NULL,'按规格报价','高文化,定制,博物馆,高客单','博物馆,机构客户,收藏者','展览,纪念,礼赠','200+','/selection/manual-craft.jpg','选品手册-之间味道.pdf 第70页','pending_review','2023','选品手册-之间味道.pdf',70,'approved','2023-01-01',220),
('craft-bamboo','craft','竹编工艺品','手工质感 · 地域文化','适合地域工艺、非遗故事和高文化含量礼赠，需要确认手工产能和交期。','竹材','编织','定制','25-30天','约35天',NULL,NULL,'按规格报价','非遗,地域文化,定制,高客单','博物馆,机构客户,文旅项目','展览,礼赠,纪念','200+','/selection/manual-craft.jpg','选品手册-之间味道.pdf 第72页','pending_review','2023','选品手册-之间味道.pdf',72,'approved','2023-01-01',230),
('precious-coin','precious','贵金属纪念章/币','高客单收藏 · 严格确认','适合纪念性项目和高端礼赠，价格必须以实时金价、克重、工艺和资质为准。','贵金属','油压/压铸/浮雕/炫彩/彩喷/光栅','直径3-6cm，按克重','7-14天','约10天',NULL,NULL,'按实时报价','贵金属,收藏,纪念,高客单','机构客户,收藏者,高端礼赠','纪念,周年,展览','200+','/selection/manual-precious.jpg','选品手册-之间味道.pdf 第75页','pending_review','2023','选品手册-之间味道.pdf',75,'approved','2023-01-01',240),
('precious-pendant','precious','贵金属吊坠/手链','高端礼赠 · 需正式报价','适合将文化符号做成高端首饰，但必须完成贵金属纯度、克重、工艺和授权确认。','贵金属','倒模/油压','按克重','7-18天','约10天',NULL,NULL,'按实时报价','贵金属,首饰,高客单,定制','机构客户,收藏者,高端礼赠','纪念,周年,礼赠','200+','/selection/manual-precious.jpg','选品手册-之间味道.pdf 第77页','pending_review','2023','选品手册-之间味道.pdf',77,'approved','2023-01-01',250)
ON DUPLICATE KEY UPDATE name=VALUES(name),subtitle=VALUES(subtitle),description=VALUES(description),material=VALUES(material),process=VALUES(process),specification=VALUES(specification),sample_lead_time=VALUES(sample_lead_time),bulk_lead_time=VALUES(bulk_lead_time),retail_min=VALUES(retail_min),retail_max=VALUES(retail_max),retail_display=VALUES(retail_display),tags=VALUES(tags),audience_tags=VALUES(audience_tags),occasion_tags=VALUES(occasion_tags),budget_band=VALUES(budget_band),cover_image_url=VALUES(cover_image_url),image_source=VALUES(image_source),source_page=VALUES(source_page),updated_at=CURRENT_TIMESTAMP;
