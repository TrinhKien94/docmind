package com.example.docmind.service.astrbot;

import com.example.docmind.dto.astrbot.AstrBotCronJobDto;
import com.example.docmind.dto.astrbot.AstrBotExtensionDto;
import com.example.docmind.dto.astrbot.AstrBotFieldSchemaDto;
import com.example.docmind.dto.astrbot.AstrBotPersonaDto;
import com.example.docmind.dto.astrbot.AstrBotPlatformTypeDto;
import com.example.docmind.dto.astrbot.AstrBotProviderDto;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Danh mục chuẩn hoá toàn bộ 19 Platform Adapters, Schema cấu hình, Personas, Providers,
 * Cron Jobs và Star Plugins/MCP Servers theo đúng kiến trúc AstrBot (https://github.com/astrbotdevs/astrbot).
 */
public final class AstrBotPlatformCatalog {

    private AstrBotPlatformCatalog() {
    }

    public static List<AstrBotPlatformTypeDto> buildPlatformTypes() {
        List<AstrBotPlatformTypeDto> list = new ArrayList<>();

        // 1. Telegram
        list.add(new AstrBotPlatformTypeDto(
                "telegram",
                "telegram_bot",
                "Telegram Bot API",
                "global_im",
                "Bộ chuyển đổi chính thức cho Telegram Bot hỗ trợ Long Polling, Unified Webhook, tự động đăng ký lệnh /command và gửi media.",
                "telegram",
                "https://docs.astrbot.app/platform/telegram.html",
                true,
                true,
                true,
                false,
                mapOf(
                        "id", "telegram_bot",
                        "type", "telegram",
                        "enable", true,
                        "telegram_token", "",
                        "start_message", "Xin chào! Tôi là Trợ lý Tư vấn Đa nền tảng DocMind x AstrBot.",
                        "telegram_api_base_url", "https://api.telegram.org/bot",
                        "telegram_file_base_url", "https://api.telegram.org/file/bot",
                        "telegram_command_register", true,
                        "telegram_command_auto_refresh", true,
                        "telegram_polling_restart_delay", 5.0,
                        "unified_webhook_mode", true
                ),
                schemaOf(
                        field("telegram_token", "Telegram Bot Token", "string", "Token cấp bởi @BotFather (VD: 123456:ABC-DEF...).", true, true, null, ""),
                        field("start_message", "Tin nhắn chào /start", "string", "Nội dung phản hồi khi người dùng gõ /start.", false, false, null, "Xin chào! Tôi là Trợ lý Tư vấn Đa nền tảng DocMind x AstrBot."),
                        field("telegram_api_base_url", "Telegram API Base URL", "string", "Địa chỉ máy chủ Bot API (mặc định dùng máy chủ chính thức).", false, false, null, "https://api.telegram.org/bot"),
                        field("telegram_command_register", "Tự động đăng ký Slash Commands", "bool", "Đồng bộ danh sách lệnh Star (/help, /reset, /persona) lên menu Telegram.", false, false, null, true),
                        field("unified_webhook_mode", "Chế độ Unified Webhook", "bool", "Nhận sự kiện qua cổng Webhook hợp nhất của AstrBot.", false, false, null, true)
                )
        ));

        // 2. Discord
        list.add(new AstrBotPlatformTypeDto(
                "discord",
                "discord_bot",
                "Discord Bot (Gateway & Interactions)",
                "community_gaming",
                "Kết nối máy chủ Discord qua Bot Gateway & Slash Interactions, hỗ trợ kênh guild, tin nhắn DM, thread và trạng thái hoạt động.",
                "discord",
                "https://docs.astrbot.app/platform/discord.html",
                false,
                true,
                true,
                false,
                mapOf(
                        "id", "discord_bot",
                        "type", "discord",
                        "enable", true,
                        "discord_token", "",
                        "discord_proxy", "",
                        "discord_command_register", true,
                        "discord_activity_name", "DocMind x AstrBot Tư vấn RAG",
                        "discord_allow_bot_messages", false,
                        "unified_webhook_mode", true
                ),
                schemaOf(
                        field("discord_token", "Discord Bot Token", "string", "Bot Token từ Discord Developer Portal (yêu cầu bật Message Content Intent).", true, true, null, ""),
                        field("discord_activity_name", "Trạng thái hiển thị (Activity)", "string", "Dòng trạng thái đang chơi/đang xem của Bot trên Discord.", false, false, null, "DocMind x AstrBot Tư vấn RAG"),
                        field("discord_command_register", "Đăng ký Application Commands", "bool", "Tự động đăng ký Slash Commands trên Guild/Global.", false, false, null, true),
                        field("discord_allow_bot_messages", "Cho phép phản hồi Bot khác", "bool", "Xử lý cả tin nhắn đến từ các tài khoản Bot khác.", false, false, null, false),
                        field("discord_proxy", "HTTP/SOCKS Proxy", "string", "Proxy tuỳ chọn khi kết nối Discord Gateway.", false, false, null, "")
                )
        ));

        // 3. Slack
        list.add(new AstrBotPlatformTypeDto(
                "slack",
                "slack_workspace",
                "Slack (Socket Mode & Webhook)",
                "global_im",
                "Tích hợp không gian làm việc Slack hỗ trợ cả Socket Mode (xapp-) và Events API Unified Webhook với xác thực chữ ký HMAC.",
                "slack",
                "https://docs.astrbot.app/platform/slack.html",
                false,
                true,
                true,
                false,
                mapOf(
                        "id", "slack_workspace",
                        "type", "slack",
                        "enable", true,
                        "bot_token", "",
                        "app_token", "",
                        "signing_secret", "",
                        "slack_connection_mode", "webhook",
                        "unified_webhook_mode", true
                ),
                schemaOf(
                        field("bot_token", "Slack Bot Token (xoxb-...)", "string", "Bot User OAuth Token bắt đầu bằng xoxb-.", true, true, null, ""),
                        field("slack_connection_mode", "Phương thức kết nối", "select", "Chọn 'socket' (cần App Token) hoặc 'webhook' (cần Signing Secret).", false, true, List.of("webhook", "socket"), "webhook"),
                        field("app_token", "Slack App-Level Token (xapp-...)", "string", "Dùng cho chế độ Socket Mode.", true, false, null, ""),
                        field("signing_secret", "Slack Signing Secret", "string", "Dùng để xác thực chữ ký X-Slack-Signature ở chế độ Webhook.", true, false, null, ""),
                        field("unified_webhook_mode", "Chế độ Unified Webhook", "bool", "Sử dụng URL Webhook hợp nhất của AstrBot.", false, false, null, true)
                )
        ));

        // 4. Feishu / Lark
        list.add(new AstrBotPlatformTypeDto(
                "lark",
                "lark_feishu",
                "Feishu / Lark (飞书开放平台)",
                "enterprise_china",
                "Bộ chuyển đổi Feishu / Lark hỗ trợ WebSocket Long Connection, Unified Webhook và tạo Bot 1-chạm bằng mã QR.",
                "lark",
                "https://docs.astrbot.app/platform/lark.html",
                true,
                true,
                true,
                true,
                mapOf(
                        "id", "lark_feishu",
                        "type", "lark",
                        "enable", true,
                        "app_id", "",
                        "app_secret", "",
                        "domain", "https://open.feishu.cn",
                        "lark_connection_mode", "socket",
                        "lark_encrypt_key", "",
                        "lark_verification_token", "",
                        "unified_webhook_mode", true
                ),
                schemaOf(
                        field("app_id", "App ID (cli_...)", "string", "App ID trên Feishu / Lark Developer Console.", false, true, null, ""),
                        field("app_secret", "App Secret", "string", "App Secret của ứng dụng Lark/Feishu.", true, true, null, ""),
                        field("domain", "Open Platform Domain", "select", "Chọn open.feishu.cn hoặc open.larksuite.com (Quốc tế).", false, false, List.of("https://open.feishu.cn", "https://open.larksuite.com"), "https://open.feishu.cn"),
                        field("lark_connection_mode", "Chế độ kết nối", "select", "WebSocket dài (socket) hoặc HTTP Callback (webhook).", false, false, List.of("socket", "webhook"), "socket"),
                        field("lark_encrypt_key", "Encrypt Key", "string", "Khoá giải mã sự kiện (nếu bật mã hoá Webhook).", true, false, null, ""),
                        field("lark_verification_token", "Verification Token", "string", "Token xác minh sự kiện Webhook Lark.", true, false, null, "")
                )
        ));

        // 5. DingTalk
        list.add(new AstrBotPlatformTypeDto(
                "dingtalk",
                "dingtalk_bot",
                "DingTalk (钉钉企业机器人)",
                "enterprise_china",
                "Kết nối DingTalk Enterprise Bot qua giao thức Stream Mode & AI Card Streaming, hỗ trợ đăng ký nhanh qua mã QR.",
                "dingtalk",
                "https://docs.astrbot.app/platform/dingtalk.html",
                true,
                true,
                false,
                true,
                mapOf(
                        "id", "dingtalk_bot",
                        "type", "dingtalk",
                        "enable", true,
                        "client_id", "",
                        "client_secret", "",
                        "card_template_id", ""
                ),
                schemaOf(
                        field("client_id", "Client ID (AppKey)", "string", "Client ID / AppKey của ứng dụng DingTalk.", false, true, null, ""),
                        field("client_secret", "Client Secret (AppSecret)", "string", "Client Secret của ứng dụng DingTalk.", true, true, null, ""),
                        field("card_template_id", "AI Card Template ID", "string", "ID mẫu thẻ tương tác DingTalk (tuỳ chọn để bật streaming).", false, false, null, "")
                )
        ));

        // 6. LINE Messaging API
        list.add(new AstrBotPlatformTypeDto(
                "line",
                "line_official",
                "LINE Messaging API",
                "global_im",
                "Kết nối tài khoản LINE Official Account qua LINE Messaging API Webhook với xác thực HMAC-SHA256 X-Line-Signature.",
                "line",
                "https://docs.astrbot.app/platform/line.html",
                false,
                true,
                true,
                false,
                mapOf(
                        "id", "line_official",
                        "type", "line",
                        "enable", true,
                        "channel_access_token", "",
                        "channel_secret", "",
                        "unified_webhook_mode", true
                ),
                schemaOf(
                        field("channel_access_token", "LINE Channel Access Token", "string", "Long-lived Channel Access Token từ LINE Developers Console.", true, true, null, ""),
                        field("channel_secret", "LINE Channel Secret", "string", "Channel Secret dùng kiểm tra chữ ký X-Line-Signature.", true, true, null, ""),
                        field("unified_webhook_mode", "Chế độ Unified Webhook", "bool", "Nhận sự kiện qua đường dẫn Webhook hợp nhất.", false, false, null, true)
                )
        ));

        // 7. Facebook Messenger (Bridged with DocMind Meta Graph API v21.0)
        list.add(new AstrBotPlatformTypeDto(
                "facebook_messenger",
                "messenger_dior_page",
                "Facebook Messenger (Meta Graph API)",
                "global_im",
                "Tích hợp trực tiếp Fanpage Facebook Messenger qua Meta Graph API v21.0, hỗ trợ X-Hub-Signature-256, Quick Replies & phản hồi EvoAgentX.",
                "messenger",
                "https://developers.facebook.com/docs/messenger-platform",
                false,
                true,
                true,
                false,
                mapOf(
                        "id", "messenger_dior_page",
                        "type", "facebook_messenger",
                        "enable", true,
                        "page_access_token", "",
                        "verify_token", "docmind-messenger-token",
                        "app_secret", "",
                        "api_version", "v21.0",
                        "send_feedback_quick_replies", true,
                        "unified_webhook_mode", true
                ),
                schemaOf(
                        field("page_access_token", "Page Access Token", "string", "Token truy cập Fanpage dùng gọi Graph Send API.", true, false, null, ""),
                        field("verify_token", "Webhook Verify Token", "string", "Token đối chiếu khi Meta gọi GET hub.challenge.", true, true, null, "docmind-messenger-token"),
                        field("app_secret", "Meta App Secret", "string", "Khoá bí mật kiểm tra chữ ký HMAC-SHA256 X-Hub-Signature-256.", true, false, null, ""),
                        field("api_version", "Graph API Version", "string", "Phiên bản Meta Graph API (mặc định v21.0).", false, false, null, "v21.0"),
                        field("send_feedback_quick_replies", "Đính kèm nút đánh giá EvoAgentX", "bool", "Tự động gửi nút 👍 Hài lòng / 👎 Cần cải thiện dưới câu trả lời.", false, false, null, true)
                )
        ));

        // 8. WeCom App & Customer Service (企业微信应用 & 微信客服)
        list.add(new AstrBotPlatformTypeDto(
                "wecom",
                "wecom_enterprise",
                "WeCom App & KF (企业微信应用 & 微信客服)",
                "enterprise_china",
                "Hỗ trợ ứng dụng nội bộ WeCom và WeChat Customer Service (WeChat KF) qua Unified Webhook mã hoá AES.",
                "wecom",
                "https://docs.astrbot.app/platform/wecom.html",
                false,
                true,
                true,
                false,
                mapOf(
                        "id", "wecom_enterprise",
                        "type", "wecom",
                        "enable", true,
                        "corpid", "",
                        "secret", "",
                        "agentid", "",
                        "token", "",
                        "encoding_aes_key", "",
                        "kf_name", "",
                        "api_base_url", "https://qyapi.weixin.qq.com/cgi-bin/",
                        "unified_webhook_mode", true
                ),
                schemaOf(
                        field("corpid", "Corp ID (企业 ID)", "string", "Mã định danh doanh nghiệp trên WeCom Admin.", false, true, null, ""),
                        field("secret", "App Secret", "string", "Secret của ứng dụng WeCom hoặc WeChat KF.", true, true, null, ""),
                        field("agentid", "Agent ID", "string", "AgentId của ứng dụng doanh nghiệp.", false, false, null, ""),
                        field("token", "Callback Token", "string", "Token xác thực URL Callback.", true, false, null, ""),
                        field("encoding_aes_key", "EncodingAESKey", "string", "Khoá mã hoá bản tin 43 ký tự.", true, false, null, ""),
                        field("kf_name", "Tên tài khoản WeChat KF", "string", "Điền nếu sử dụng chế độ Chăm sóc khách hàng WeChat KF.", false, false, null, "")
                )
        ));

        // 9. WeCom AI Bot (企业微信智能机器人)
        list.add(new AstrBotPlatformTypeDto(
                "wecom_ai_bot",
                "wecom_smart_bot",
                "WeCom AI Bot (企业微信智能机器人)",
                "enterprise_china",
                "Kết nối Trợ lý AI thông minh của WeCom hỗ trợ cả Long Connection WebSocket (openws.work.weixin.qq.com) và Unified Webhook.",
                "wecom_ai",
                "https://docs.astrbot.app/platform/wecom_ai_bot.html",
                true,
                false,
                true,
                false,
                mapOf(
                        "id", "wecom_smart_bot",
                        "type", "wecom_ai_bot",
                        "enable", true,
                        "wecom_ai_bot_connection_mode", "long_connection",
                        "wecom_ai_bot_name", "DocMind AI Concierge",
                        "wecomaibot_ws_bot_id", "",
                        "wecomaibot_ws_secret", "",
                        "wecomaibot_token", "",
                        "wecomaibot_encoding_aes_key", "",
                        "wecomaibot_ws_url", "wss://openws.work.weixin.qq.com",
                        "unified_webhook_mode", true
                ),
                schemaOf(
                        field("wecom_ai_bot_connection_mode", "Chế độ kết nối", "select", "Kết nối dài WebSocket (long_connection) hoặc Webhook.", false, true, List.of("long_connection", "webhook"), "long_connection"),
                        field("wecomaibot_ws_bot_id", "WebSocket Bot ID", "string", "Bot ID khi dùng chế độ Long Connection.", false, false, null, ""),
                        field("wecomaibot_ws_secret", "WebSocket Secret", "string", "Secret kết nối WebSocket.", true, false, null, ""),
                        field("wecomaibot_token", "Webhook Token", "string", "Token khi dùng chế độ Webhook.", true, false, null, ""),
                        field("wecomaibot_encoding_aes_key", "Webhook EncodingAESKey", "string", "Khoá AES khi dùng chế độ Webhook.", true, false, null, "")
                )
        ));

        // 10. WeChat Official Account (微信公众平台)
        list.add(new AstrBotPlatformTypeDto(
                "weixin_official_account",
                "wechat_oa",
                "WeChat Official Account (微信公众平台)",
                "enterprise_china",
                "Kết nối Tài khoản Công chúng WeChat (Subscription / Service Account) qua Unified Webhook & chế độ gửi chủ động.",
                "wechat",
                "https://docs.astrbot.app/platform/weixin_official_account.html",
                false,
                true,
                true,
                false,
                mapOf(
                        "id", "wechat_oa",
                        "type", "weixin_official_account",
                        "enable", true,
                        "appid", "",
                        "secret", "",
                        "token", "",
                        "encoding_aes_key", "",
                        "api_base_url", "https://api.weixin.qq.com/cgi-bin/",
                        "active_send_mode", false,
                        "unified_webhook_mode", true
                ),
                schemaOf(
                        field("appid", "AppID (开发者 ID)", "string", "AppID của tài khoản WeChat Official Account.", false, true, null, ""),
                        field("secret", "AppSecret", "string", "AppSecret của tài khoản WeChat Official Account.", true, true, null, ""),
                        field("token", "Token xác thực", "string", "Token cấu hình trên máy chủ WeChat OA.", true, true, null, ""),
                        field("encoding_aes_key", "EncodingAESKey", "string", "Khoá mã hoá tin nhắn (nếu dùng chế độ bảo mật).", true, false, null, ""),
                        field("active_send_mode", "Chế độ gửi chủ động (Customer Service API)", "bool", "Dùng API gửi chủ động cho tài khoản đã xác thực.", false, false, null, false)
                )
        ));

        // 11. Personal WeChat iLink (个人微信)
        list.add(new AstrBotPlatformTypeDto(
                "weixin_oc",
                "weixin_personal",
                "Personal WeChat iLink (个人微信扫码)",
                "enterprise_china",
                "Kết nối WeChat cá nhân qua giao thức iLink với tính năng quét mã QR đăng nhập trực tiếp trên bảng điều khiển.",
                "wechat_qr",
                "https://docs.astrbot.app/platform/weixin_oc.html",
                false,
                true,
                false,
                true,
                mapOf(
                        "id", "weixin_personal",
                        "type", "weixin_oc",
                        "enable", true,
                        "weixin_oc_base_url", "https://ilinkai.weixin.qq.com",
                        "weixin_oc_bot_type", "3",
                        "weixin_oc_qr_poll_interval", 1
                ),
                schemaOf(
                        field("weixin_oc_base_url", "iLink Base URL", "string", "Địa chỉ máy chủ iLink WeChat.", false, true, null, "https://ilinkai.weixin.qq.com"),
                        field("weixin_oc_bot_type", "Bot Type", "string", "Mã loại thiết bị đăng nhập (mặc định 3).", false, false, null, "3"),
                        field("weixin_oc_qr_poll_interval", "Chu kỳ kiểm tra QR (giây)", "int", "Khoảng cách giữa các lần kiểm tra trạng thái quét mã QR.", false, false, null, 1)
                )
        ));

        // 12. QQ Official Bot (WebSocket)
        list.add(new AstrBotPlatformTypeDto(
                "qq_official",
                "qq_official_ws",
                "QQ Official Bot (WebSocket, Khuyên dùng)",
                "enterprise_china",
                "Kết nối QQ Official Bot qua WebSocket hoặc quét mã QR đăng nhập nhanh, hỗ trợ nhóm, C2C và kênh Guild.",
                "qq",
                "https://docs.astrbot.app/platform/qqofficial.html",
                false,
                true,
                false,
                true,
                mapOf(
                        "id", "qq_official_ws",
                        "type", "qq_official",
                        "enable", true,
                        "appid", "",
                        "secret", "",
                        "enable_group_c2c", true,
                        "enable_guild_direct_message", true,
                        "use_markdown", true
                ),
                schemaOf(
                        field("appid", "QQ Bot AppID", "string", "AppID từ nền tảng QQ Open Platform.", false, true, null, ""),
                        field("secret", "QQ Bot AppSecret", "string", "AppSecret từ nền tảng QQ Open Platform.", true, true, null, ""),
                        field("enable_group_c2c", "Bật tin nhắn Nhóm & C2C", "bool", "Nhận sự kiện chat nhóm QQ và chat riêng C2C.", false, false, null, true),
                        field("enable_guild_direct_message", "Bật tin nhắn Kênh (Guild DM)", "bool", "Nhận sự kiện từ QQ Guild.", false, false, null, true),
                        field("use_markdown", "Sử dụng định dạng Markdown", "bool", "Gửi phản hồi dưới dạng QQ Markdown.", false, false, null, true)
                )
        ));

        // 13. QQ Official Bot (Webhook)
        list.add(new AstrBotPlatformTypeDto(
                "qq_official_webhook",
                "qq_official_wh",
                "QQ Official Bot (Unified Webhook)",
                "enterprise_china",
                "Kết nối QQ Official Bot qua HTTP Callback Unified Webhook có xác thực chữ ký Ed25519.",
                "qq_webhook",
                "https://docs.astrbot.app/platform/qqofficial_webhook.html",
                false,
                true,
                true,
                true,
                mapOf(
                        "id", "qq_official_wh",
                        "type", "qq_official_webhook",
                        "enable", true,
                        "appid", "",
                        "secret", "",
                        "use_markdown", true,
                        "is_sandbox", false,
                        "unified_webhook_mode", true
                ),
                schemaOf(
                        field("appid", "QQ Bot AppID", "string", "AppID của QQ Bot.", false, true, null, ""),
                        field("secret", "QQ Bot AppSecret", "string", "AppSecret dùng sinh chữ ký Ed25519.", true, true, null, ""),
                        field("is_sandbox", "Môi trường Sandbox", "bool", "Bật nếu bot đang ở chế độ thử nghiệm Sandbox.", false, false, null, false),
                        field("unified_webhook_mode", "Chế độ Unified Webhook", "bool", "Nhận callback qua cổng hợp nhất của AstrBot.", false, false, null, true)
                )
        ));

        // 14. OneBot v11 (aiocqhttp / NapCat / Lagrange)
        list.add(new AstrBotPlatformTypeDto(
                "aiocqhttp",
                "onebot_v11",
                "OneBot v11 (NapCat / LLOneBot / Lagrange)",
                "community_gaming",
                "Chuẩn giao thức OneBot v11 qua Reverse WebSocket tương thích với NapCat, LLOneBot, Lagrange và Shamrock.",
                "onebot",
                "https://docs.astrbot.app/platform/aiocqhttp.html",
                false,
                true,
                true,
                false,
                mapOf(
                        "id", "onebot_v11",
                        "type", "aiocqhttp",
                        "enable", true,
                        "ws_reverse_host", "0.0.0.0",
                        "ws_reverse_port", 6199,
                        "ws_reverse_token", "",
                        "unified_webhook_mode", true
                ),
                schemaOf(
                        field("ws_reverse_host", "Reverse WebSocket Host", "string", "Địa chỉ IP lắng nghe kết nối Reverse WS.", false, true, null, "0.0.0.0"),
                        field("ws_reverse_port", "Reverse WebSocket Port", "int", "Cổng lắng nghe kết nối từ NapCat/OneBot (mặc định 6199).", false, true, null, 6199),
                        field("ws_reverse_token", "Access Token", "string", "Token bảo mật xác thực kết nối OneBot v11.", true, false, null, "")
                )
        ));

        // 15. Satori Protocol
        list.add(new AstrBotPlatformTypeDto(
                "satori",
                "satori_universal",
                "Satori Universal Protocol",
                "community_gaming",
                "Giao thức IM đa nền tảng chuẩn Satori v1 hỗ trợ kết nối đồng thời nhiều nền tảng qua Satori SDK.",
                "satori",
                "https://docs.astrbot.app/platform/satori.html",
                false,
                true,
                false,
                false,
                mapOf(
                        "id", "satori_universal",
                        "type", "satori",
                        "enable", true,
                        "satori_api_base_url", "http://localhost:5140/satori/v1",
                        "satori_endpoint", "ws://localhost:5140/satori/v1/events",
                        "satori_token", "",
                        "satori_auto_reconnect", true,
                        "satori_heartbeat_interval", 10
                ),
                schemaOf(
                        field("satori_api_base_url", "Satori API Base URL", "string", "Địa chỉ REST API của máy chủ Satori.", false, true, null, "http://localhost:5140/satori/v1"),
                        field("satori_endpoint", "Satori WebSocket Events URL", "string", "Địa chỉ WebSocket nhận sự kiện Satori.", false, true, null, "ws://localhost:5140/satori/v1/events"),
                        field("satori_token", "Satori Bearer Token", "string", "Token xác thực máy chủ Satori.", true, false, null, "")
                )
        ));

        // 16. KOOK (开黑啦)
        list.add(new AstrBotPlatformTypeDto(
                "kook",
                "kook_bot",
                "KOOK (开黑啦 Gaming Chat)",
                "community_gaming",
                "Kết nối cộng đồng KOOK qua WebSocket Gateway với cơ chế tự động kết nối lại & heartbeat thông minh.",
                "kook",
                "https://docs.astrbot.app/platform/kook.html",
                false,
                true,
                false,
                false,
                mapOf(
                        "id", "kook_bot",
                        "type", "kook",
                        "enable", true,
                        "kook_bot_token", "",
                        "kook_heartbeat_interval", 30,
                        "kook_reconnect_delay", 1
                ),
                schemaOf(
                        field("kook_bot_token", "KOOK Bot Token", "string", "Token từ KOOK Developer Center.", true, true, null, ""),
                        field("kook_heartbeat_interval", "Chu kỳ Heartbeat (giây)", "int", "Thời gian gửi ping giữ kết nối (mặc định 30s).", false, false, null, 30)
                )
        ));

        // 17. Mattermost
        list.add(new AstrBotPlatformTypeDto(
                "mattermost",
                "mattermost_team",
                "Mattermost Team Chat",
                "global_im",
                "Tích hợp nền tảng chat doanh nghiệp mã nguồn mở Mattermost qua REST API v4 và WebSocket.",
                "mattermost",
                "https://mattermost.com",
                false,
                true,
                true,
                false,
                mapOf(
                        "id", "mattermost_team",
                        "type", "mattermost",
                        "enable", true,
                        "mattermost_url", "https://chat.example.com",
                        "mattermost_bot_token", "",
                        "mattermost_reconnect_delay", 5.0,
                        "unified_webhook_mode", true
                ),
                schemaOf(
                        field("mattermost_url", "Mattermost Server URL", "string", "Địa chỉ máy chủ Mattermost của tổ chức.", false, true, null, "https://chat.example.com"),
                        field("mattermost_bot_token", "Mattermost Bot Access Token", "string", "Personal Access Token hoặc Bot Token.", true, true, null, "")
                )
        ));

        // 18. Misskey (Fediverse)
        list.add(new AstrBotPlatformTypeDto(
                "misskey",
                "misskey_fediverse",
                "Misskey (Fediverse Microblog & Chat)",
                "community_gaming",
                "Kết nối máy chủ Misskey trong mạng lưới Fediverse, hỗ trợ Note Mentions, Direct Chat và tải lên tệp.",
                "misskey",
                "https://misskey-hub.net",
                false,
                true,
                false,
                false,
                mapOf(
                        "id", "misskey_fediverse",
                        "type", "misskey",
                        "enable", true,
                        "misskey_instance_url", "https://misskey.io",
                        "misskey_token", "",
                        "misskey_default_visibility", "public",
                        "misskey_enable_chat", true
                ),
                schemaOf(
                        field("misskey_instance_url", "Misskey Instance URL", "string", "URL máy chủ Misskey (VD: https://misskey.io).", false, true, null, "https://misskey.io"),
                        field("misskey_token", "Misskey API Token", "string", "Token quyền đọc/ghi Note và Chat.", true, true, null, ""),
                        field("misskey_default_visibility", "Phạm vi hiển thị mặc định", "select", "Phạm vi bài đăng phản hồi.", false, false, List.of("public", "home", "followers", "specified"), "public")
                )
        ));

        // 19. WebChat (Built-in AstrBot WebUI)
        list.add(new AstrBotPlatformTypeDto(
                "webchat",
                "astrbot_webchat",
                "AstrBot Built-in WebChat",
                "web_bridge",
                "Kênh chat trực tuyến tích hợp sẵn trên giao diện quản trị WebUI của AstrBot & DocMind.",
                "webchat",
                "https://docs.astrbot.app",
                true,
                true,
                true,
                false,
                mapOf(
                        "id", "astrbot_webchat",
                        "type", "webchat",
                        "enable", true,
                        "webchat_present_type", "fullscreen",
                        "enable_streaming", true,
                        "unified_webhook_mode", true
                ),
                schemaOf(
                        field("webchat_present_type", "Kiểu hiển thị", "select", "Chế độ toàn màn hình (fullscreen) hoặc nhúng (embedded).", false, false, List.of("fullscreen", "embedded"), "fullscreen"),
                        field("enable_streaming", "Bật phản hồi Streaming", "bool", "Truyền phát câu trả lời theo thời gian thực.", false, false, null, true)
                )
        ));

        return list;
    }

    public static List<AstrBotPersonaDto> buildDefaultPersonas() {
        String now = Instant.now().toString();
        return List.of(
                new AstrBotPersonaDto(
                        "dior_luxury_advisor",
                        "Chuyên gia Tư vấn Christian Dior Couture & Parfums",
                        "Luxury Retail",
                        "Bạn là Chuyên gia Tư vấn Cao cấp (Senior Client Advisor) của Christian Dior. Luôn xưng hô lịch thiệp, sang trọng, trích dẫn chuẩn xác tài liệu SOP Dior (Skincare Capture Totale / Prestige, Makeup Forever / Rouge Dior, Nước hoa Sauvage / Miss Dior / J'adore, Chính sách VIP & Đổi trả) và tuyệt đối tuân thủ quy tắc an toàn thai kỳ.",
                        List.of(
                                "Khách hàng: Chào shop, tư vấn giúp mình chu trình dưỡng da Dior.",
                                "Trợ lý: Dạ Christian Dior Maison xin kính chào Quý khách! Em rất hân hạnh được đồng hành tư vấn chu trình dưỡng da chuẩn Haute Couture phù hợp nhất với làn da của Quý khách ạ."
                        ),
                        List.of("rag_hybrid_search", "dior_sop_lookup", "evoagentx_memory", "citation_verifier"),
                        0.3,
                        "SALES",
                        now
                ),
                new AstrBotPersonaDto(
                        "docmind_rag_specialist",
                        "Chuyên gia Phân tích Tài liệu Grounded RAG",
                        "Enterprise Knowledge",
                        "Bạn là Chuyên gia Phân tích Tài liệu của DocMind. Mọi câu trả lời phải bám sát 100% vào các đoạn trích (chunks) từ tài liệu đã tải lên, kèm trích dẫn [Doc/Page] minh bạch, không suy diễn ngoài văn bản.",
                        List.of(
                                "Khách hàng: Tóm tắt các điều khoản chính trong tài liệu này.",
                                "Trợ lý: Dạ, dựa trên tài liệu đã được lập chỉ mục trong hệ thống DocMind, dưới đây là bản tổng hợp chi tiết kèm trích dẫn trang và mục tương ứng:"
                        ),
                        List.of("rag_hybrid_search", "bm25_pgvector_fusion", "citation_verifier"),
                        0.2,
                        "SERVICE_SUPPORT",
                        now
                ),
                new AstrBotPersonaDto(
                        "vip_aftersales_concierge",
                        "Quản gia Chăm sóc Hậu mãi & Bảo hành VIP",
                        "Customer Care",
                        "Bạn là Quản gia Chăm sóc Hậu mãi & Bảo hành VIP. Ưu tiên lắng nghe đồng cảm, giải quyết khiếu nại, kiểm tra điều kiện đổi trả nguyên seal trong 7-14 ngày, xác thực mã batch code và hướng dẫn quy trình bảo hành tận tâm.",
                        List.of(
                                "Khách hàng: Sản phẩm tôi nhận được bị lỗi vòi xịt, tôi muốn đổi trả.",
                                "Trợ lý: Dạ em vô cùng xin lỗi Quý khách vì trải nghiệm chưa trọn vẹn này. Em xin phép hướng dẫn ngay quy trình ưu tiên đổi mới 1-1 miễn phí trong 48 giờ cho Quý khách ạ."
                        ),
                        List.of("dior_sop_lookup", "warranty_policy_checker", "evoagentx_memory"),
                        0.25,
                        "WARRANTY_RETURN",
                        now
                ),
                new AstrBotPersonaDto(
                        "astrbot_omni_assistant",
                        "AstrBot Trợ lý Đa Nền tảng Hợp nhất",
                        "Multi-Platform Core",
                        "Bạn là AstrBot — Trợ lý AI Đa nền tảng kết nối Telegram, Discord, Slack, Feishu/Lark, LINE, DingTalk, WeCom, WeChat, QQ và Facebook Messenger với kho tri thức RAG DocMind và động cơ tự tiến hoá EvoAgentX.",
                        List.of(
                                "Khách hàng: /help",
                                "Trợ lý: Xin chào! Bạn có thể đặt câu hỏi tư vấn sản phẩm/tài liệu trực tiếp hoặc sử dụng các lệnh /status, /persona, /scenario, /evolve, /reset."
                        ),
                        List.of("rag_hybrid_search", "star_command_router", "mcp_bridge", "evoagentx_memory"),
                        0.4,
                        "AUTO",
                        now
                )
        );
    }

    public static List<AstrBotProviderDto> buildDefaultProviders() {
        return List.of(
                new AstrBotProviderDto(
                        "9router-codex-gpt6-astra",
                        "9Router / OpenAI Codex",
                        "openai_chat_completion",
                        "chat_completion",
                        true,
                        "http://127.0.0.1:20128/v1",
                        "codex/gpt-6-astra",
                        List.of("codex/gpt-6-astra", "gpt-6-astra", "gpt-5.4"),
                        120,
                        true,
                        "sk-9router-••••••astra",
                        "connected",
                        42L
                ),
                new AstrBotProviderDto(
                        "google-gemini-rag",
                        "Google Gemini API",
                        "gemini_chat_completion",
                        "chat_completion",
                        true,
                        "https://generativelanguage.googleapis.com/v1beta",
                        "gemini-2.5-flash",
                        List.of("gemini-2.5-flash", "gemini-2.5-pro"),
                        90,
                        true,
                        "AIzaSy••••••DocMind",
                        "connected",
                        68L
                ),
                new AstrBotProviderDto(
                        "docmind-hybrid-embedder",
                        "DocMind Vector + BM25 Embedder",
                        "docmind_embedding",
                        "embedding",
                        true,
                        "http://127.0.0.1:8091/v1/embed",
                        "bge-m3-multilingual-1024",
                        List.of("bge-m3-multilingual-1024", "text-embedding-004"),
                        30,
                        true,
                        "internal-service-token",
                        "connected",
                        15L
                ),
                new AstrBotProviderDto(
                        "docmind-mmr-reranker",
                        "DocMind RRF + MMR Cross-Reranker",
                        "docmind_rerank",
                        "rerank",
                        true,
                        "http://127.0.0.1:8090/internal/rerank",
                        "rrf-mmr-hybrid-v2",
                        List.of("rrf-mmr-hybrid-v2", "bge-reranker-v2-m3"),
                        15,
                        true,
                        "internal-rerank-token",
                        "connected",
                        8L
                ),
                new AstrBotProviderDto(
                        "edge-tts-multilingual",
                        "Microsoft Edge Neural TTS",
                        "edge_tts",
                        "speech_synthesis",
                        true,
                        "wss://speech.platform.bing.com/consumer/speech/synthesize",
                        "vi-VN-HoaiMyNeural",
                        List.of("vi-VN-HoaiMyNeural", "vi-VN-NamMinhNeural", "en-US-AriaNeural"),
                        30,
                        true,
                        "edge-tts-public",
                        "connected",
                        55L
                )
        );
    }

    public static List<AstrBotCronJobDto> buildDefaultCronJobs() {
        String now = Instant.now().toString();
        return List.of(
                new AstrBotCronJobDto(
                        "cron-dior-vip-followup",
                        "Chăm sóc khách hàng VIP sau mua hàng (7 ngày)",
                        "Tự động gửi lời hỏi thăm trải nghiệm sử dụng serum Capture Totale & nhắc lịch tái khám da.",
                        "0 9 * * *",
                        "telegram_bot:PrivateMessage:vip_client_8801",
                        "Hãy soạn tin nhắn chăm sóc khách hàng VIP sau 7 ngày sử dụng Dior Capture Totale Le Sérum kèm hướng dẫn bảo quản.",
                        "SERVICE_SUPPORT",
                        false,
                        true,
                        now,
                        "Hàng ngày lúc 09:00",
                        4,
                        "succeeded",
                        "Dạ Christian Dior xin kính chào Quý khách! Qua 7 ngày trải nghiệm Capture Totale Le Sérum..."
                ),
                new AstrBotCronJobDto(
                        "cron-evoagentx-nightly-optimize",
                        "Chu kỳ Tự tiến hoá EvoAgentX (TextGrad + MIPRO)",
                        "Tổng hợp phản hồi đa nền tảng trong ngày và tối ưu hoá bộ quy tắc hướng dẫn theo từng kịch bản.",
                        "30 23 * * *",
                        "discord_bot:GroupMessage:ops_channel_01",
                        "Thực thi chu kỳ tối ưu hoá EvoAgentX cho kịch bản SALES và WARRANTY_RETURN, gửi báo cáo tóm tắt.",
                        "SALES",
                        false,
                        true,
                        now,
                        "Hàng ngày lúc 23:30",
                        12,
                        "succeeded",
                        "Đã hoàn tất chu kỳ tiến hoá EvoAgentX: cập nhật +2 quy tắc TextGrad và +1 mẫu MIPRO."
                ),
                new AstrBotCronJobDto(
                        "cron-rag-kb-health-digest",
                        "Báo cáo Trạng thái Kho Tri thức RAG & Nền tảng",
                        "Kiểm tra tình trạng kết nối 19 nền tảng AstrBot và chỉ mục tài liệu SOP mỗi sáng thứ Hai.",
                        "0 8 * * 1",
                        "slack_workspace:GroupMessage:C07DOCMIND",
                        "Tổng hợp trạng thái hoạt động của các Bot Adapter và danh sách tài liệu SOP READY.",
                        "GENERAL",
                        false,
                        true,
                        now,
                        "Thứ Hai hàng tuần lúc 08:00",
                        2,
                        "succeeded",
                        "Báo cáo hệ thống: 6 tài liệu SOP Dior READY, các cổng Webhook hoạt động ổn định."
                )
        );
    }

    public static List<AstrBotExtensionDto> buildDefaultExtensions() {
        return List.of(
                new AstrBotExtensionDto(
                        "star-docmind-rag-bridge",
                        "astrbot_plugin_docmind_rag",
                        "DocMind Hybrid RAG & Citation Verifier",
                        "star_plugin",
                        "DocMind Core Team",
                        "2.4.0",
                        "Kết nối trực tiếp mọi sự kiện tin nhắn AstrBot với công cụ tìm kiếm lai (pgvector + BM25 + Local SOP KB) và kiểm chứng trích dẫn.",
                        true,
                        List.of("/kb", "/search"),
                        4,
                        "internal://docmind-rag"
                ),
                new AstrBotExtensionDto(
                        "star-evoagentx-optimizer",
                        "astrbot_plugin_evoagentx",
                        "EvoAgentX Self-Evolving Prompt Engine",
                        "star_plugin",
                        "DocMind x EvoAgentX",
                        "1.8.0",
                        "Tự động chấm điểm câu trả lời đa kênh qua hàm mất mát 4 tiêu chí TextGrad và thăng cấp mẫu Few-Shot MIPRO.",
                        true,
                        List.of("/evolve", "/guidelines"),
                        3,
                        "internal://evoagentx"
                ),
                new AstrBotExtensionDto(
                        "star-builtin-commands",
                        "astrbot_builtin_commands",
                        "Bộ Lệnh Quản trị Nhanh AstrBot (Star Commands)",
                        "slash_command",
                        "AstrBot Devs",
                        "4.2.0",
                        "Cung cấp các lệnh điều khiển phiên trực tiếp trong khung chat: /help, /status, /reset, /persona, /scenario, /whoami.",
                        true,
                        List.of("/help", "/status", "/reset", "/persona", "/scenario", "/whoami"),
                        6,
                        "builtin://commands"
                ),
                new AstrBotExtensionDto(
                        "mcp-codesearch-engine",
                        "mcp_server_codesearch",
                        "Codesearch BM25 & AST MCP Server",
                        "mcp_server",
                        "DocMind Infra",
                        "1.2.0",
                        "Máy chủ MCP truy xuất mã nguồn và bản đồ cấu trúc dự án tốc độ sub-millisecond (FTS5 BM25 + PageRank).",
                        true,
                        List.of(),
                        5,
                        "stdio://codesearch.exe serve"
                ),
                new AstrBotExtensionDto(
                        "mcp-task-tracker",
                        "mcp_server_task_tracker",
                        "Task Tracker & Knowledge Docs MCP Server",
                        "mcp_server",
                        "DocMind Infra",
                        "1.5.0",
                        "Máy chủ MCP quản lý ticket công việc, tài liệu kiến trúc và liên kết tiến độ dự án qua PostgreSQL.",
                        true,
                        List.of(),
                        12,
                        "stdio://mcp.exe (port 15432)"
                )
        );
    }

    private static AstrBotFieldSchemaDto field(
            String key,
            String description,
            String type,
            String hint,
            boolean secret,
            boolean required,
            List<String> options,
            Object defaultValue) {
        return new AstrBotFieldSchemaDto(key, description, type, hint, secret, true, required, options, defaultValue);
    }

    private static Map<String, AstrBotFieldSchemaDto> schemaOf(AstrBotFieldSchemaDto... fields) {
        Map<String, AstrBotFieldSchemaDto> map = new LinkedHashMap<>();
        for (AstrBotFieldSchemaDto f : fields) {
            map.put(f.key(), f);
        }
        return map;
    }

    private static Map<String, Object> mapOf(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            map.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return map;
    }
}
