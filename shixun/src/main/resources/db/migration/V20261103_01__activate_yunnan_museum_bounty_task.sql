-- 开放「云南省博物馆文创设计征集令」悬赏任务：
-- 由 draft 激活为 active，并设置 90 天截稿日期，让 C 端「悬赏令」可见并可投稿。
UPDATE consumer_bounty_task
SET status = 'active',
    deadline = DATE_ADD(CURDATE(), INTERVAL 90 DAY),
    brief = '围绕云南省博物馆馆藏文物进行原创文创设计，品类包括潮玩、饰品、日用好物、数码周边、纸品、数字文创。建议从滇王金印、牛虎铜案、四牛鎏金骑士铜贮贝器、贮贝器盖雕像、铜鼓等馆藏重器中提炼纹样、造型与色彩元素。提交作品需填写产品名称、品类、特色功能、文物原型/灵感来源、产品介绍，并上传全色正面、侧面、背面三视图（每张仅支持 JPG，1–3 MB）。投稿一经遴选采用，平台将联系作者洽谈合作及销售分成，并可获免费树脂打样。每个账号最多提交 5 件作品（以身份证号为唯一判定）。',
    updated_at = NOW()
WHERE campaign_key = 'yunnan_museum_cultural_product_2026'
  AND status = 'draft';