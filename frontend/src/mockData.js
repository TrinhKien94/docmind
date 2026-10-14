/**
 * Dữ liệu Demo khi chưa khởi động Docker / backend Spring Boot
 * Cung cấp mock data phong phú hỗ trợ trải nghiệm offline mượt mà cho:
 * 1. Danh sách tài liệu và hỏi đáp RAG (DocMind)
 * 2. Tự tiến hóa học hỏi EvoAgentX (TextGrad & MIPRO)
 * 3. Tích hợp Facebook Messenger & Trình giả lập Bot
 */

export const initialMockDocuments = [
  {
    document_id: "doc-sample-ai-arch-01",
    filename: "DocMind-Architecture-Specification.pdf",
    status: "READY",
    pages: 18,
    engine_breakdown: {
      pymupdf: 12,
      docling: 6
    },
    created_at: new Date(Date.now() - 3600000).toISOString()
  },
  {
    document_id: "doc-sample-financial-02",
    filename: "Bao_Cao_Tai_Chinh_Q3_2025.docx",
    status: "READY",
    pages: 34,
    engine_breakdown: {
      docx_office: 34
    },
    created_at: new Date(Date.now() - 7200000).toISOString()
  },
  {
    document_id: "doc-sample-scan-ocr-03",
    filename: "Hop_Dong_Mua_Ban_Scan.pdf",
    status: "PARSING",
    pages: 5,
    engine_breakdown: {
      paddleocr: 3
    },
    created_at: new Date(Date.now() - 120000).toISOString()
  },
  {
    document_id: "doc-sample-corrupt-04",
    filename: "Corrupted_Archive_Data.bin",
    status: "FAILED",
    pages: 0,
    engine_breakdown: {},
    created_at: new Date(Date.now() - 18000000).toISOString()
  }
]

export const sampleQA = {
  "kiến trúc": {
    answer: "DocMind là hệ thống RAG độc lập dựa trên tài liệu (phong cách NotebookLLM). Kiến trúc bao gồm hai dịch vụ chính và PostgreSQL:\n\n1. **`api-java` (Port 8090)**: Orchestrator viết bằng Spring Boot 4, quản lý vòng đời ingest, chunking, hybrid retrieval (pgvector + BM25 Reciprocal Rank Fusion), gọi LLM và xác thực trích dẫn (citation validation).\n2. **`doc-service-py` (Port 8091)**: FastAPI parser trích xuất các block chuẩn hóa từ PDF, DOCX, PPTX với PyMuPDF, Docling và PaddleOCR.\n3. **`PostgreSQL 16`**: Lưu trữ vector embedding và schema chuẩn hóa qua Flyway.\n\nĐặc biệt, luồng chat hoàn toàn chạy trên Java và **không bao giờ gọi sang Python service** lúc suy luận.",
    citations: [
      {
        chunk_id: "chk-arch-001",
        document_id: "doc-sample-ai-arch-01",
        block_ids: ["blk-01", "blk-02"],
        page: 1,
        bbox: [72.0, 100.5, 540.0, 320.0],
        heading_path: ["Architecture", "Overview"],
        snippet: "Two services plus Postgres: api-java (8090) orchestrator and chat/RAG brain; doc-service-py (8091) FastAPI parser."
      },
      {
        chunk_id: "chk-arch-002",
        document_id: "doc-sample-ai-arch-01",
        block_ids: ["blk-05"],
        page: 2,
        bbox: [72.0, 450.0, 520.0, 590.0],
        heading_path: ["Architecture", "Chat Path"],
        snippet: "The chat path is Java-only. During chat, api-java reads already-indexed data and never calls doc-service-py."
      }
    ]
  },
  "default": {
    answer: "Dựa trên các tài liệu đã tải lên được chọn, hệ thống tìm thấy thông tin phù hợp và trích xuất nguyên văn kèm theo số trang và khối văn bản tương ứng.",
    citations: [
      {
        chunk_id: "chk-gen-01",
        document_id: "doc-sample-ai-arch-01",
        block_ids: ["blk-10"],
        page: 3,
        bbox: [50.0, 120.0, 500.0, 240.0],
        heading_path: ["Ingest Flow", "Hybrid Retrieval"],
        snippet: "Hybrid retrieval kết hợp Dense Vector Search qua pgvector và Sparse Lexical Search qua BM25, hợp nhất bằng Reciprocal Rank Fusion (RRF k=60)."
      }
    ]
  }
}

/**
 * Mock cấu hình prompt & kịch bản khách hàng phục vụ chế độ offline
 */
export const mockCustomerPrompts = {
  scenarios: [
    { scenario: 'SALES', display_name: 'Mua hàng & Báo giá' },
    { scenario: 'SERVICE_SUPPORT', display_name: 'Sử dụng Dịch vụ & Kỹ thuật' },
    { scenario: 'WARRANTY_RETURN', display_name: 'Bảo hành & Đổi trả' },
    { scenario: 'ORDER_BILLING', display_name: 'Đơn hàng & Thanh toán' }
  ],
  default_customer_role: 'Khách hàng mua hàng hoặc sử dụng dịch vụ'
}

/**
 * =========================================================================
 * 1. FACEBOOK MESSENGER MOCK DATA (Offline Demo & Fallback)
 * =========================================================================
 */

export const mockMessengerStatus = {
  enabled: true,
  verify_token_configured: true,
  page_token_configured: true,
  app_secret_configured: true,
  verify_signature: true,
  active_sessions_count: 3,
  webhook_url: 'http://localhost:8080/api/messenger/webhook',
  graph_base_url: 'https://graph.facebook.com',
  api_version: 'v21.0',
  recent_interactions: [
    {
      interaction_id: 'inter-msg-001',
      sender_id: 'psid_vn_8829104',
      incoming_text: 'Shop ơi tai nghe Sony WH-1000XM5 còn màu bạc không và bảo hành bao lâu?',
      reply_text: 'Dạ chào Anh/Chị! Mẫu tai nghe chống ồn Sony WH-1000XM5 màu Bạc (Silver) hiện đang còn sẵn hàng tại kho ạ. Sản phẩm được bảo hành chính hãng Sony Việt Nam 12 tháng và hỗ trợ 1-đổi-1 trong 15 ngày đầu nếu có lỗi phần cứng từ nhà sản xuất. Anh/Chị có muốn em giữ hàng hoặc lên đơn ngay không ạ?',
      scenario: 'SALES',
      delivery_status: 'SENT',
      quick_replies_count: 3,
      timestamp: '2026-10-14T06:55:00Z',
      simulated: false
    },
    {
      interaction_id: 'inter-msg-002',
      sender_id: 'psid_vn_3391024',
      incoming_text: 'Mình vừa nhận máy nhưng kết nối Bluetooth với iPhone bị chập chờn, xử lý thế nào bạn?',
      reply_text: 'Dạ rất tiếc vì sự cố Anh/Chị gặp phải ạ! Để khắc phục tình trạng này, Anh/Chị thử: 1. Quên thiết bị trong Bluetooth của iPhone; 2. Giữ nút nguồn tai nghe 7 giây đến khi đèn nháy xanh-đỏ để reset pairing; 3. Kết nối lại qua app Sony Headphones Connect. Nếu vẫn không ổn định, em sẽ tạo phiếu kiểm tra bảo hành tận nơi cho mình ngay ạ.',
      scenario: 'SERVICE_SUPPORT',
      delivery_status: 'SENT',
      quick_replies_count: 3,
      timestamp: '2026-10-14T06:30:00Z',
      simulated: false
    },
    {
      interaction_id: 'inter-msg-003',
      sender_id: 'psid_vn_5518290',
      incoming_text: 'Cho mình hỏi đơn hàng #DM-9921 đã được giao cho shipper chưa?',
      reply_text: 'Dạ đơn hàng #DM-9921 của Anh/Chị đã được đóng gói và bàn giao cho đơn vị vận chuyển Viettel Post lúc 09:15 sáng nay. Mã vận đơn là VT8892113VN, dự kiến giao đến địa chỉ của Anh/Chị trong ngày mai ạ.',
      scenario: 'ORDER_BILLING',
      delivery_status: 'SIMULATED',
      quick_replies_count: 3,
      timestamp: '2026-10-14T06:10:00Z',
      simulated: true
    }
  ]
}

/**
 * Trình giả lập phản hồi Facebook Messenger bot thông minh (Demo offline)
 * Tạo phản hồi tiếng Việt tự nhiên kèm 3 Quick Replies (mỗi nút <= 20 ký tự theo chuẩn Meta)
 */
export function mockSimulateReply(senderId = 'customer_psid_test', message = '', scenario = 'AUTO') {
  const text = (message || '').toLowerCase()
  let resolvedScenario = scenario
  let replyText = ''
  let quickReplies = []

  if (resolvedScenario === 'AUTO' || !resolvedScenario) {
    if (text.includes('bảo hành') || text.includes('đổi') || text.includes('lỗi') || text.includes('rè') || text.includes('hỏng')) {
      resolvedScenario = 'WARRANTY_RETURN'
    } else if (text.includes('cài đặt') || text.includes('kết nối') || text.includes('bluetooth') || text.includes('sửa') || text.includes('pin') || text.includes('app')) {
      resolvedScenario = 'SERVICE_SUPPORT'
    } else if (text.includes('đơn hàng') || text.includes('hóa đơn') || text.includes('vat') || text.includes('thanh toán') || text.includes('giao')) {
      resolvedScenario = 'ORDER_BILLING'
    } else {
      resolvedScenario = 'SALES'
    }
  }

  switch (resolvedScenario) {
    case 'SALES':
      replyText = `Dạ chào Anh/Chị! Cảm ơn Anh/Chị đã quan tâm đến DocMind. Về câu hỏi "${message || 'sản phẩm'}", hiện bên em đang có sẵn hàng với chính sách bảo hành chính hãng và hỗ trợ giao hỏa tốc 2 giờ. Em xin phép gửi báo giá chi tiết và chương trình quà tặng kèm ngay ạ!`
      quickReplies = ['Báo giá chi tiết', 'Đặt hàng ngay', 'Chính sách trả góp'] // 16, 13, 18 ký tự (<= 20)
      break
    case 'SERVICE_SUPPORT':
      replyText = `Dạ DocMind xin ghi nhận câu hỏi kỹ thuật của Anh/Chị: "${message}". Để khắc phục nhanh, Anh/Chị thử: 1. Khởi động lại thiết bị; 2. Cập nhật firmware mới nhất; 3. Đặt lại kết nối Bluetooth. Nếu cần kiểm tra sâu hơn, kỹ thuật viên bên em sẽ hỗ trợ trực tuyến ngay ạ.`
      quickReplies = ['Hướng dẫn reset', 'Gửi video mẫu', 'Gặp nhân viên'] // 16, 14, 14 ký tự (<= 20)
      break
    case 'WARRANTY_RETURN':
      replyText = `Dạ chào Anh/Chị! DocMind rất tiếc vì sự cố phát sinh. Chính sách của bên em là 1-đổi-1 trong 15 ngày đầu đối với lỗi nhà sản xuất và bảo hành 12 tháng tận nơi. Anh/Chị giữ lại hộp và phụ kiện, em sẽ tạo mã yêu cầu đổi mới nhanh cho mình ạ.`
      quickReplies = ['Quy trình 1 đổi 1', 'Tra cứu bảo hành', 'Gửi hàng về shop'] // 18, 16, 16 ký tự (<= 20)
      break
    case 'ORDER_BILLING':
      replyText = `Dạ về yêu cầu đơn hàng & thanh toán: "${message}", hệ thống ghi nhận trạng thái bình thường. Hóa đơn VAT điện tử sẽ được xuất và gửi qua email trong vòng 24 giờ làm việc sau khi đơn hàng được kích hoạt thành công ạ.`
      quickReplies = ['Kiểm tra mã vận đơn', 'Xuất hóa đơn VAT', 'Thay đổi địa chỉ'] // 19, 16, 17 ký tự (<= 20)
      break
    default:
      replyText = `Dạ DocMind xin chào Anh/Chị! Em đã ghi nhận câu hỏi "${message}". Em luôn sẵn sàng giải đáp mọi thông tin về sản phẩm, dịch vụ và hỗ trợ kỹ thuật tận tâm ạ.`
      quickReplies = ['Báo giá nhanh', 'Tư vấn trực tiếp', 'Xem thêm thông tin'] // 13, 16, 19 ký tự (<= 20)
      break
  }

  const interactionId = `sim-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 6)}`
  const timestamp = new Date().toISOString()

  // Cập nhật vào danh sách tương tác gần đây
  const newInteraction = {
    interaction_id: interactionId,
    sender_id: senderId,
    incoming_text: message || 'Xin chào bot',
    reply_text: replyText,
    scenario: resolvedScenario,
    delivery_status: 'SIMULATED',
    quick_replies_count: quickReplies.length,
    timestamp: timestamp,
    simulated: true
  }

  if (mockMessengerStatus && Array.isArray(mockMessengerStatus.recent_interactions)) {
    mockMessengerStatus.recent_interactions.unshift(newInteraction)
    if (mockMessengerStatus.recent_interactions.length > 25) {
      mockMessengerStatus.recent_interactions.pop()
    }
  }

  return {
    sender_id: senderId,
    received_message: message,
    scenario: resolvedScenario,
    reply_sent: replyText,
    quick_replies: quickReplies,
    interaction_id: interactionId,
    simulated: true,
    timestamp: timestamp
  }
}

/**
 * Xóa danh sách phiên hội thoại Messenger trong bộ nhớ mock
 */
export function mockClearMessengerSessions(senderId) {
  const count = mockMessengerStatus.active_sessions_count
  mockMessengerStatus.active_sessions_count = 0
  if (senderId) {
    mockMessengerStatus.recent_interactions = mockMessengerStatus.recent_interactions.filter(
      i => i.sender_id !== senderId
    )
  } else {
    mockMessengerStatus.recent_interactions = []
  }
  return {
    message: 'Đã xóa toàn bộ phiên hội thoại Messenger (Mẫu)',
    cleared_count: count,
    timestamp: new Date().toISOString()
  }
}

/**
 * =========================================================================
 * 2. EVOAGENTX SELF-EVOLUTION MOCK DATA (Offline Demo & Fallback)
 * =========================================================================
 */

export const mockEvolutionData = {
  enabled: true,
  strategy: 'evoagentx-mipro-textgrad',
  evolution_generation: 4,
  total_interactions: 148,
  total_feedback_count: 62,
  average_quality_score: 0.942,

  // 8 Learned Guidelines đúc kết từ TextGrad (2 quy tắc cho mỗi kịch bản chính)
  learned_guidelines: {
    SALES: [
      {
        id: 'lg-sales-01',
        scenario: 'SALES',
        rule: 'Khi khách hàng hỏi giá hoặc so sánh sản phẩm, luôn chủ động nêu rõ tình trạng sẵn hàng, quà tặng kèm và chính sách giao hàng miễn phí trước khi đề xuất chốt đơn.',
        source_gradient: 'Khách phản hồi tích cực khi được thông báo miễn phí ship và quà tặng kèm ngay ở câu đầu.',
        confidence_score: 0.96,
        reinforcement_count: 14,
        content_hash: 'hash-sales-01',
        updated_at: '2026-10-14T06:30:00Z'
      },
      {
        id: 'lg-sales-02',
        scenario: 'SALES',
        rule: 'Với đơn hàng giá trị trên 5 triệu đồng, luôn gợi ý thêm chương trình trả góp 0% qua thẻ tín dụng và thời hạn bảo hành mở rộng.',
        source_gradient: 'Tỷ lệ chốt đơn tăng khi khách hàng được tư vấn phương án thanh toán chia nhỏ linh hoạt.',
        confidence_score: 0.92,
        reinforcement_count: 9,
        content_hash: 'hash-sales-02',
        updated_at: '2026-10-14T06:15:00Z'
      }
    ],
    SERVICE_SUPPORT: [
      {
        id: 'lg-supp-01',
        scenario: 'SERVICE_SUPPORT',
        rule: 'Khi xử lý sự cố kỹ thuật hoặc lỗi kết nối, phân tách giải pháp thành từng bước đánh số rõ ràng (1, 2, 3) và hỏi lại khách hàng xem bước nào gặp vướng mắc.',
        source_gradient: 'Khách hàng đánh giá dễ thực hiện hơn khi các thao tác cài đặt được chia nhỏ theo từng bước tuần tự.',
        confidence_score: 0.95,
        reinforcement_count: 18,
        content_hash: 'hash-supp-01',
        updated_at: '2026-10-14T06:20:00Z'
      },
      {
        id: 'lg-supp-02',
        scenario: 'SERVICE_SUPPORT',
        rule: 'Luôn kiểm tra phiên bản hệ điều hành hoặc firmware của thiết bị khách hàng trước khi hướng dẫn thao tác sâu để tránh nhầm lẫn giao diện.',
        source_gradient: 'Tránh hướng dẫn sai tính năng trên các phiên bản hệ điều hành cũ hơn.',
        confidence_score: 0.89,
        reinforcement_count: 7,
        content_hash: 'hash-supp-02',
        updated_at: '2026-10-14T05:45:00Z'
      }
    ],
    WARRANTY_RETURN: [
      {
        id: 'lg-warr-01',
        scenario: 'WARRANTY_RETURN',
        rule: 'Luôn khẳng định cam kết 1-đổi-1 trong 7 ngày đầu và thái độ lắng nghe, đồng cảm với sự bất tiện của khách trước khi hướng dẫn thủ tục đổi trả.',
        source_gradient: 'Giảm mức độ căng thẳng của khách hàng khi sản phẩm mới mua gặp lỗi ban đầu.',
        confidence_score: 0.98,
        reinforcement_count: 22,
        content_hash: 'hash-warr-01',
        updated_at: '2026-10-14T06:40:00Z'
      },
      {
        id: 'lg-warr-02',
        scenario: 'WARRANTY_RETURN',
        rule: 'Hướng dẫn khách hàng giữ lại hộp, phụ kiện và hóa đơn mua hàng để rút ngắn thời gian thẩm định bảo hành xuống dưới 24 giờ.',
        source_gradient: 'Khách gửi đủ phụ kiện giúp trung tâm bảo hành đổi mới ngay mà không phải chờ duyệt.',
        confidence_score: 0.91,
        reinforcement_count: 11,
        content_hash: 'hash-warr-02',
        updated_at: '2026-10-14T05:30:00Z'
      }
    ],
    ORDER_BILLING: [
      {
        id: 'lg-ord-01',
        scenario: 'ORDER_BILLING',
        rule: 'Khi khách hàng hỏi về hóa đơn VAT, xác nhận thông tin doanh nghiệp (Tên công ty, MST, Email nhận hóa đơn) và cam kết phát hành hóa đơn điện tử trong 24 giờ làm việc.',
        source_gradient: 'Khách hàng doanh nghiệp đánh giá 5 sao khi hóa đơn điện tử gửi về email đúng hẹn.',
        confidence_score: 0.94,
        reinforcement_count: 15,
        content_hash: 'hash-ord-01',
        updated_at: '2026-10-14T05:55:00Z'
      },
      {
        id: 'lg-ord-02',
        scenario: 'ORDER_BILLING',
        rule: 'Với thắc mắc về thời gian giao hàng, luôn cung cấp mã vận đơn cụ thể và liên kết trực tiếp tới trang tra cứu hành trình của đối tác vận chuyển.',
        source_gradient: 'Cung cấp mã tracking trực tiếp giúp khách an tâm theo dõi và giảm tỷ lệ hỏi lại.',
        confidence_score: 0.93,
        reinforcement_count: 12,
        content_hash: 'hash-ord-02',
        updated_at: '2026-10-14T05:10:00Z'
      }
    ]
  },

  // 4 Few-Shot Exemplars chất lượng cao được tối ưu hóa qua MIPRO (1 mẫu cho mỗi kịch bản)
  evolved_few_shot_examples: {
    SALES: [
      {
        id: 'mipro-ex-01',
        scenario: 'SALES',
        customer_question: 'Mình đang phân vân giữa tai nghe Sony WH-1000XM5 và Bose QuietComfort Ultra, bên bạn có so sánh không?',
        assistant_answer: 'Dạ em xin phân tích nhanh 2 dòng tai nghe flagship này để Anh/Chị dễ lựa chọn ạ:\n1. **Sony WH-1000XM5**: Nổi bật với chất âm chi tiết, pin trâu 30 giờ, công nghệ đàm thoại lọc gió tốt và phần mềm tùy biến EQ đa dạng.\n2. **Bose QuietComfort Ultra**: Thiết kế gập gọn linh hoạt, đệm tai cực kỳ êm ái khi đeo kính và khả năng chống ồn thụ động rất êm tai.\n\nHiện tại DocMind đang có sẵn máy trải nghiệm tại showroom kèm quà tặng bao đựng cao cấp và hỗ trợ trả góp 0%. Anh/Chị hay nghe thể loại nhạc nào để em tư vấn kỹ hơn ạ?',
        quality_score: 0.96,
        reinforcement_count: 8,
        content_hash: 'hash-mipro-01',
        created_at: '2026-10-14T06:00:00Z'
      }
    ],
    SERVICE_SUPPORT: [
      {
        id: 'mipro-ex-02',
        scenario: 'SERVICE_SUPPORT',
        customer_question: 'Tai nghe của mình bị mất kết nối một bên tai trái, có cách nào tự sửa tại nhà không?',
        assistant_answer: 'Dạ rất tiếc vì sự cố này ạ! Anh/Chị có thể khắc phục nhanh theo 3 bước sau:\n1. Đặt cả 2 tai vào hộp sạc, cắm nguồn sạc trong ít nhất 15 phút.\n2. Mở nắp hộp sạc, nhấn giữ nút ở mặt sau 10 giây cho đến khi đèn LED nháy cam rồi chuyển xanh để reset hoàn toàn.\n3. Quên thiết bị trên điện thoại và tiến hành kết nối lại.\n\nNếu sau 3 bước tai trái vẫn chưa nhận tín hiệu, em sẽ tạo phiếu đổi tai mới cho Anh/Chị ngay ạ!',
        quality_score: 0.95,
        reinforcement_count: 11,
        content_hash: 'hash-mipro-02',
        created_at: '2026-10-14T05:40:00Z'
      }
    ],
    WARRANTY_RETURN: [
      {
        id: 'mipro-ex-03',
        scenario: 'WARRANTY_RETURN',
        customer_question: 'Sản phẩm mới mua 3 ngày bị lỗi rè loa thì đổi mới như thế nào?',
        assistant_answer: 'Dạ em rất tiếc về sự cố rè loa trên sản phẩm mới của Anh/Chị! DocMind cam kết chính sách 1-đổi-1 trong 15 ngày đầu đối với mọi lỗi phần cứng từ nhà sản xuất.\n\nAnh/Chị chỉ cần giữ nguyên thân máy kèm hộp và phụ kiện ban đầu, gửi về trung tâm hỗ trợ gần nhất hoặc báo địa chỉ để bưu tá qua nhận hàng tận nhà hoàn toàn miễn phí. Em xin phép tạo mã tiếp nhận đổi mới ngay bây giờ ạ!',
        quality_score: 0.98,
        reinforcement_count: 14,
        content_hash: 'hash-mipro-03',
        created_at: '2026-10-14T05:20:00Z'
      }
    ],
    ORDER_BILLING: [
      {
        id: 'mipro-ex-04',
        scenario: 'ORDER_BILLING',
        customer_question: 'Mình muốn thanh toán chuyển khoản và nhận hóa đơn VAT cho công ty được không?',
        assistant_answer: 'Dạ hoàn toàn được ạ! Bên em hỗ trợ chuyển khoản ngân hàng qua mã VietQR tự động xác nhận sau 30 giây.\n\nVề hóa đơn VAT, sau khi thanh toán Anh/Chị chỉ cần điền Tên công ty, Mã số thuế và Email nhận hóa đơn tại form xác nhận; phòng kế toán sẽ phát hành hóa đơn điện tử hợp lệ gửi vào email của Anh/Chị trong vòng 24 giờ làm việc ạ.',
        quality_score: 0.93,
        reinforcement_count: 7,
        content_hash: 'hash-mipro-04',
        created_at: '2026-10-14T05:00:00Z'
      }
    ]
  },

  // 6 Bản ghi phản tư Textual Gradients Log (Loss Critique + Textual Gradient + Action)
  recent_gradients: [
    {
      interaction_id: 'int-grad-01',
      scenario: 'SALES',
      question_excerpt: 'Có freeship nội thành Hà Nội không em?',
      quality_score: 0.96,
      loss_critique: 'Câu trả lời thân thiện, lập tức xác nhận freeship và cam kết thời gian giao hàng 2 giờ.',
      textual_gradient: 'Nhấn mạnh chính sách giao hỏa tốc 2h không phụ thu phí ship nội thành.',
      applied_action: 'EVOLVED_GUIDELINE_UPSERT (gen=4)',
      timestamp: '2026-10-14T06:55:00Z'
    },
    {
      interaction_id: 'int-grad-02',
      scenario: 'SERVICE_SUPPORT',
      question_excerpt: 'App không nhận tai nghe khi bật định vị trên iOS',
      quality_score: 0.92,
      loss_critique: 'Đã chỉ rõ cần cấp quyền Precise Location trên iOS 18 để ứng dụng tìm kiếm Bluetooth BLE.',
      textual_gradient: 'Bổ sung hướng dẫn cấp quyền vị trí chính xác cho Sony Headphones App.',
      applied_action: 'MIPRO_EXEMPLAR_OPTIMIZED (gen=4)',
      timestamp: '2026-10-14T06:40:00Z'
    },
    {
      interaction_id: 'int-grad-03',
      scenario: 'WARRANTY_RETURN',
      question_excerpt: 'Bảo hành ở tỉnh xa thì gửi hàng thế nào?',
      quality_score: 0.95,
      loss_critique: 'Hướng dẫn đóng gói an toàn và xác nhận shop chi trả cước gửi 2 chiều bưu điện.',
      textual_gradient: 'Khẳng định khách hàng ở tỉnh được miễn phí cước vận chuyển bảo hành hai chiều.',
      applied_action: 'EVOLVED_GUIDELINE_UPSERT (gen=3)',
      timestamp: '2026-10-14T06:15:00Z'
    },
    {
      interaction_id: 'int-grad-04',
      scenario: 'ORDER_BILLING',
      question_excerpt: 'Đổi địa chỉ giao hàng sau khi đặt được không shop?',
      quality_score: 0.89,
      loss_critique: 'Cần hỗ trợ đổi địa chỉ nhanh qua hotline trước khi đơn xuất kho chuyển sang bưu cục.',
      textual_gradient: 'Cung cấp hotline khẩn cấp 1800-xxxx để can thiệp kịp thời trước khi bưu tá lấy hàng.',
      applied_action: 'EVOLVED_GUIDELINE_UPSERT (gen=3)',
      timestamp: '2026-10-14T05:50:00Z'
    },
    {
      interaction_id: 'int-grad-05',
      scenario: 'SALES',
      question_excerpt: 'Mua 2 chiếc tai nghe có được chiết khấu thêm không?',
      quality_score: 0.94,
      loss_critique: 'Tự động áp dụng voucher combo giảm 5% và tặng thêm cáp sạc nhanh Type-C.',
      textual_gradient: 'Chủ động đề xuất mã giảm giá combo cho đơn hàng từ 2 sản phẩm trở lên.',
      applied_action: 'MIPRO_EXEMPLAR_OPTIMIZED (gen=2)',
      timestamp: '2026-10-14T05:25:00Z'
    },
    {
      interaction_id: 'int-grad-06',
      scenario: 'GENERAL',
      question_excerpt: 'Cửa hàng mở cửa đến mấy giờ buổi tối?',
      quality_score: 0.91,
      loss_critique: 'Nêu chính xác khung giờ mở cửa 8h00 - 21h30 tất cả các ngày trong tuần.',
      textual_gradient: 'Bổ sung thông tin hotline tư vấn trực tuyến hoạt động 24/7 kể cả ngày lễ.',
      applied_action: 'EVOLVED_GUIDELINE_UPSERT (gen=1)',
      timestamp: '2026-10-14T05:00:00Z'
    }
  ]
}

/**
 * Kích hoạt một chu kỳ tự tiến hóa giả lập khi backend offline
 */
export function mockTriggerEvolution(scenario) {
  const sc = scenario || 'SALES'
  mockEvolutionData.evolution_generation += 1
  mockEvolutionData.total_interactions += 1
  mockEvolutionData.average_quality_score = Math.min(
    0.99,
    Number((mockEvolutionData.average_quality_score + 0.005).toFixed(3))
  )

  const newGradient = {
    interaction_id: `int-grad-sim-${Date.now()}`,
    scenario: sc,
    question_excerpt: `Tự động tối ưu hóa trọng số prompt kịch bản ${sc}`,
    quality_score: 0.96,
    loss_critique: `Đã phản tư loss evaluation và điều chỉnh gradient ngôn ngữ cho ${sc} thành công.`,
    textual_gradient: `Tăng cường độ đồng cảm, bổ sung đề xuất chủ động và câu hỏi dẫn dắt tiếp theo.`,
    applied_action: `EVOLVED_GUIDELINE_UPSERT (gen=${mockEvolutionData.evolution_generation})`,
    timestamp: new Date().toISOString()
  }

  mockEvolutionData.recent_gradients.unshift(newGradient)
  if (mockEvolutionData.recent_gradients.length > 20) {
    mockEvolutionData.recent_gradients.pop()
  }

  return JSON.parse(JSON.stringify(mockEvolutionData))
}
