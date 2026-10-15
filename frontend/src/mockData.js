/**
 * Dữ liệu Demo khi chưa khởi động Docker / backend Spring Boot
 * Cung cấp mock data phong phú hỗ trợ trải nghiệm offline mượt mà cho:
 * 1. Danh sách tài liệu và hỏi đáp RAG (DocMind)
 * 2. Tự tiến hóa học hỏi EvoAgentX (TextGrad & MIPRO)
 * 3. Tích hợp Facebook Messenger & Trình giả lập Bot
 */

export const initialMockDocuments = [
  {
    document_id: "doc-dior-skincare-01",
    filename: "01-dior-skincare-consultation-sop.md",
    status: "READY",
    pages: 4,
    engine_breakdown: {
      dior_sop_kb: 11
    },
    created_at: new Date(Date.now() - 1800000).toISOString()
  },
  {
    document_id: "doc-dior-makeup-02",
    filename: "02-dior-makeup-color-matching-guide.md",
    status: "READY",
    pages: 4,
    engine_breakdown: {
      dior_sop_kb: 10
    },
    created_at: new Date(Date.now() - 2100000).toISOString()
  },
  {
    document_id: "doc-dior-fragrance-03",
    filename: "03-dior-fragrance-gift-personalization.md",
    status: "READY",
    pages: 4,
    engine_breakdown: {
      dior_sop_kb: 10
    },
    created_at: new Date(Date.now() - 2400000).toISOString()
  },
  {
    document_id: "doc-dior-sales-loyalty-04",
    filename: "04-dior-sales-closing-vip-loyalty-sop.md",
    status: "READY",
    pages: 2,
    engine_breakdown: {
      dior_sop_kb: 10
    },
    created_at: new Date(Date.now() - 2550000).toISOString()
  },
  {
    document_id: "doc-dior-warranty-05",
    filename: "05-dior-authenticity-warranty-return-complaints.md",
    status: "READY",
    pages: 3,
    engine_breakdown: {
      dior_sop_kb: 7
    },
    created_at: new Date(Date.now() - 2700000).toISOString()
  },
  {
    document_id: "doc-dior-order-delivery-06",
    filename: "06-dior-order-payment-vip-packaging-delivery.md",
    status: "READY",
    pages: 2,
    engine_breakdown: {
      dior_sop_kb: 8
    },
    created_at: new Date(Date.now() - 2850000).toISOString()
  },
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
  "dior": {
    answer: "Dạ em chào Chị ạ! Rất vui được tư vấn cho mình hôm nay. Em xin chia sẻ thông tin chi tiết để Chị dễ lựa chọn nhé:\n\n- **Dưỡng da an toàn cho mẹ bầu:** Dòng tinh chất **Capture Totale Le Sérum (30ml: 3.450.000 VNĐ)** và **Dior Prestige La Micro-Huile de Rose (30ml: 7.400.000 VNĐ)** rất lành tính, dùng êm cho phụ nữ mang thai. Riêng dòng **Capture Totale Retishot (0.1% Retinol)** thì mình tránh dùng trong thai kỳ Chị nhé.\n- **Chọn kem nền & son môi:** Kem nền **Dior Forever Matte / Skin Glow (1.850.000 VNĐ)** có đủ các tông N (Trung tính), W (Ấm), CR (Lạnh) rất tiệp da; kết hợp cùng son **Rouge Dior (1.290.000 VNĐ)** màu 999, 720, 772, 100 hoặc son dưỡng **Lip Glow 004 Coral (1.150.000 VNĐ)** giúp môi tươi tắn tự nhiên.\n- **Quyền lợi chăm sóc & giao hàng:** Bên em hỗ trợ đổi trả/hoàn tiền 100% trong 14 ngày theo chính sách Cosmetovigilance nếu chẳng may da bị kích ứng dù đã mở nắp (còn >= 80%), tặng kèm hộp quà Couture + khắc tên 12 ký tự miễn phí và giao Hỏa tốc VIP 2 giờ tại Hà Nội & TP.HCM ạ!",
    citations: [
      {
        chunk_id: "chk-dior-skin-01",
        document_id: "doc-dior-skincare-01",
        block_ids: ["blk-dior-01"],
        page: 2,
        bbox: [48.0, 170.0, 547.0, 265.0],
        heading_path: ["QUY TRÌNH TƯ VẤN CHĂM SÓC DA CHUYÊN SÂU", "2.2. Dòng Capture Totale"],
        snippet: "Capture Totale Le Sérum (30ml: 3.450.000 VNĐ | 50ml: 4.650.000 VNĐ). Capture Totale Retishot (0.1% Pure Retinol) KHÔNG tư vấn cho phụ nữ đang mang thai."
      },
      {
        chunk_id: "chk-dior-makeup-02",
        document_id: "doc-dior-makeup-02",
        block_ids: ["blk-dior-02"],
        page: 1,
        bbox: [48.0, 280.0, 547.0, 375.0],
        heading_path: ["CẨM NANG TƯ VẤN TRANG ĐIỂM", "2.1. Dior Forever Matte vs Forever Skin Glow"],
        snippet: "Dior Forever Matte Foundation SPF 15 (30ml – 1.850.000 VNĐ) kiềm dầu 24h; Rouge Dior (1.290.000 VNĐ) các màu biểu tượng 999, 720, 772, 100."
      }
    ]
  },
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
 * 1. EVOAGENTX SELF-EVOLUTION MOCK DATA (Offline Demo & Fallback)
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

// =========================================================================
// 7. Mock AstrBot Multi-Platform Integration & Management Dashboard Data
// =========================================================================
export const mockAstrBotPlatformTypes = [
  {
    type: 'telegram',
    display_name: 'Telegram Bot',
    category: 'Global Messaging',
    description: 'Kết nối Telegram Bot API qua Polling hoặc Unified Webhook, hỗ trợ Group/Private chat, inline buttons và lệnh slash.',
    doc_url: 'https://docs.astrbot.app/platform/telegram.html',
    support_unified_webhook: true,
    support_qr_login: false,
    recommended_regions: ['Global', 'Vietnam'],
    supported_events: ['PrivateMessage', 'GroupMessage', 'Command', 'CallbackQuery'],
    default_config: { id: 'telegram_dior_concierge', type: 'telegram', enable: true, telegram_token: '', start_with_webhook: false, unified_webhook_mode: true },
    fields: [
      { key: 'telegram_token', label: 'Bot Token (@BotFather)', type: 'password', required: true, placeholder: '123456789:AAH...', description: 'Token cấp bởi @BotFather trên Telegram', default_value: '' },
      { key: 'start_with_webhook', label: 'Chế độ Webhook thay vì Polling', type: 'boolean', required: false, placeholder: '', description: 'Bật để nhận tin qua Unified Webhook', default_value: false }
    ]
  },
  {
    type: 'discord',
    display_name: 'Discord Bot',
    category: 'Global Messaging',
    description: 'Kết nối Discord Gateway WebSocket & Application Slash Commands cho máy chủ cộng đồng và DM.',
    doc_url: 'https://docs.astrbot.app/platform/discord.html',
    support_unified_webhook: false,
    support_qr_login: false,
    recommended_regions: ['Global', 'Vietnam'],
    supported_events: ['GroupMessage', 'PrivateMessage', 'SlashCommand', 'ThreadMessage'],
    default_config: { id: 'discord_community_hub', type: 'discord', enable: true, discord_token: '', discord_application_id: '', bot_status_text: 'Christian Dior & DocMind AI' },
    fields: [
      { key: 'discord_token', label: 'Discord Bot Token', type: 'password', required: true, placeholder: 'MTIzNDU2Nzg5...', description: 'Bot Token trong Discord Developer Portal (cần bật Message Content Intent)', default_value: '' },
      { key: 'discord_application_id', label: 'Application ID', type: 'string', required: false, placeholder: '1198273645...', description: 'ID ứng dụng Discord để đồng bộ Slash Commands', default_value: '' },
      { key: 'bot_status_text', label: 'Trạng thái hiển thị (Activity)', type: 'string', required: false, placeholder: 'Christian Dior & DocMind AI', description: 'Dòng trạng thái hiển thị dưới tên Bot', default_value: 'Christian Dior & DocMind AI' }
    ]
  },
  {
    type: 'slack',
    display_name: 'Slack App',
    category: 'Enterprise Workspace',
    description: 'Tích hợp Slack Workspace qua Socket Mode hoặc Unified Events API Webhook.',
    doc_url: 'https://docs.astrbot.app/platform/slack.html',
    support_unified_webhook: true,
    support_qr_login: false,
    recommended_regions: ['Global', 'Enterprise'],
    supported_events: ['GroupMessage', 'PrivateMessage', 'AppMention'],
    default_config: { id: 'slack_enterprise_desk', type: 'slack', enable: true, bot_token: '', signing_secret: '', connection_mode: 'webhook', unified_webhook_mode: true },
    fields: [
      { key: 'bot_token', label: 'Bot User OAuth Token (xoxb-)', type: 'password', required: true, placeholder: 'xoxb-...', description: 'Token xác thực Bot trong Slack App OAuth & Permissions', default_value: '' },
      { key: 'signing_secret', label: 'Signing Secret', type: 'password', required: true, placeholder: '8f9a2b...', description: 'Signing Secret để xác thực chữ ký HMAC từ Slack', default_value: '' },
      { key: 'connection_mode', label: 'Chế độ kết nối', type: 'select', required: true, placeholder: 'webhook', description: 'Chọn webhook hoặc socket', default_value: 'webhook', options: ['webhook', 'socket'] }
    ]
  },
  {
    type: 'lark',
    display_name: 'Lark / Feishu (飞书)',
    category: 'Enterprise Workspace',
    description: 'Kết nối Lark / Feishu doanh nghiệp qua WebSocket hoặc Unified Webhook, hỗ trợ tạo Bot nhanh bằng mã QR.',
    doc_url: 'https://docs.astrbot.app/platform/lark.html',
    support_unified_webhook: true,
    support_qr_login: true,
    recommended_regions: ['APAC', 'Vietnam', 'China'],
    supported_events: ['PrivateMessage', 'GroupMessage', 'CardAction'],
    default_config: { id: 'lark_feishu_workspace', type: 'lark', enable: true, app_id: '', app_secret: '', domain: 'https://open.larksuite.com', lark_socket_mode: true },
    fields: [
      { key: 'app_id', label: 'App ID', type: 'string', required: true, placeholder: 'cli_a58f...', description: 'App ID trên Lark/Feishu Developer Console', default_value: '' },
      { key: 'app_secret', label: 'App Secret', type: 'password', required: true, placeholder: '••••••••', description: 'App Secret của ứng dụng Lark/Feishu', default_value: '' },
      { key: 'domain', label: 'API Domain', type: 'select', required: true, placeholder: 'https://open.larksuite.com', description: 'Chọn Lark Quốc tế hoặc Feishu', default_value: 'https://open.larksuite.com', options: ['https://open.larksuite.com', 'https://open.feishu.cn'] }
    ]
  },
  {
    type: 'dingtalk',
    display_name: 'DingTalk (钉钉)',
    category: 'Enterprise Workspace',
    description: 'Kết nối trợ lý doanh nghiệp DingTalk qua Stream Mode không cần mở port công khai hoặc đăng ký bằng QR.',
    doc_url: 'https://docs.astrbot.app/platform/dingtalk.html',
    support_unified_webhook: false,
    support_qr_login: true,
    recommended_regions: ['APAC', 'China'],
    supported_events: ['PrivateMessage', 'GroupMessage'],
    default_config: { id: 'dingtalk_corp_bot', type: 'dingtalk', enable: false, client_id: '', client_secret: '' },
    fields: [
      { key: 'client_id', label: 'Client ID (AppKey)', type: 'string', required: true, placeholder: 'dingxxxx...', description: 'AppKey / Client ID trên DingTalk Open Platform', default_value: '' },
      { key: 'client_secret', label: 'Client Secret (AppSecret)', type: 'password', required: true, placeholder: '••••••••', description: 'AppSecret của Robot DingTalk', default_value: '' }
    ]
  },
  {
    type: 'line',
    display_name: 'LINE Messaging API',
    category: 'Global Messaging',
    description: 'Kết nối LINE Official Account qua Unified Webhook phục vụ khách hàng Nhật Bản, Đài Loan, Thái Lan & Đông Nam Á.',
    doc_url: 'https://docs.astrbot.app/platform/line.html',
    support_unified_webhook: true,
    support_qr_login: false,
    recommended_regions: ['Japan', 'Taiwan', 'Thailand', 'SEA'],
    supported_events: ['PrivateMessage', 'GroupMessage', 'FollowEvent'],
    default_config: { id: 'line_official_concierge', type: 'line', enable: true, channel_access_token: '', channel_secret: '', unified_webhook_mode: true },
    fields: [
      { key: 'channel_access_token', label: 'Channel Access Token (Long-lived)', type: 'password', required: true, placeholder: 'eyJhbGciOi...', description: 'Token truy cập kênh LINE Messaging API', default_value: '' },
      { key: 'channel_secret', label: 'Channel Secret', type: 'password', required: true, placeholder: '3f8a9c...', description: 'Channel Secret để xác thực X-Line-Signature', default_value: '' }
    ]
  },
  {
    type: 'wecom',
    display_name: 'WeCom的企业微信 (Enterprise)',
    category: 'WeChat Ecosystem',
    description: 'Tích hợp ứng dụng nội bộ WeCom (WeChat Work) qua Unified Webhook kèm mã hoá AES chuẩn doanh nghiệp.',
    doc_url: 'https://docs.astrbot.app/platform/wecom.html',
    support_unified_webhook: true,
    support_qr_login: false,
    recommended_regions: ['China', 'APAC'],
    supported_events: ['PrivateMessage', 'GroupMessage'],
    default_config: { id: 'wecom_vip_clienteling', type: 'wecom', enable: false, corpid: '', secret: '', agentid: '1000002', unified_webhook_mode: true },
    fields: [
      { key: 'corpid', label: 'Corp ID', type: 'string', required: true, placeholder: 'ww123456789...', description: 'ID doanh nghiệp WeCom', default_value: '' },
      { key: 'secret', label: 'Application Secret', type: 'password', required: true, placeholder: '••••••••', description: 'Secret của ứng dụng tự tạo trong WeCom', default_value: '' },
      { key: 'agentid', label: 'Agent ID', type: 'string', required: true, placeholder: '1000002', description: 'AgentId của ứng dụng WeCom', default_value: '1000002' }
    ]
  },
  {
    type: 'wecom_ai_bot',
    display_name: 'WeCom AI Bot (企业微信智能机器人)',
    category: 'WeChat Ecosystem',
    description: 'Kết nối trực tiếp WeCom Intelligent Robot qua giao thức đối thoại thông minh và Unified Webhook.',
    doc_url: 'https://docs.astrbot.app/platform/wecom_ai_bot.html',
    support_unified_webhook: true,
    support_qr_login: false,
    recommended_regions: ['China', 'APAC'],
    supported_events: ['PrivateMessage', 'GroupMessage'],
    default_config: { id: 'wecom_smart_assistant', type: 'wecom_ai_bot', enable: false, token: '', encoding_aes_key: '', unified_webhook_mode: true },
    fields: [
      { key: 'token', label: 'Bot Token', type: 'password', required: true, placeholder: 'wecom_ai_token', description: 'Token cấu hình trên trang WeCom AI Bot', default_value: '' },
      { key: 'encoding_aes_key', label: 'EncodingAESKey', type: 'password', required: true, placeholder: '43 ký tự AES Key', description: 'Khoá giải mã gói tin sự kiện WeCom AI Bot', default_value: '' }
    ]
  },
  {
    type: 'weixin_official_account',
    display_name: 'WeChat Official Account (微信公众号)',
    category: 'WeChat Ecosystem',
    description: 'Kết nối Tài khoản Chính thức WeChat (Service/Subscription Account) qua Unified Webhook.',
    doc_url: 'https://docs.astrbot.app/platform/weixin_official_account.html',
    support_unified_webhook: true,
    support_qr_login: false,
    recommended_regions: ['China', 'Global Luxury Retail'],
    supported_events: ['PrivateMessage', 'SubscribeEvent', 'MenuClick'],
    default_config: { id: 'wechat_oa_dior_boutique', type: 'weixin_official_account', enable: false, appid: '', secret: '', token: '', unified_webhook_mode: true },
    fields: [
      { key: 'appid', label: 'Developer AppID', type: 'string', required: true, placeholder: 'wx8899...', description: 'AppID của WeChat Official Account', default_value: '' },
      { key: 'secret', label: 'Developer AppSecret', type: 'password', required: true, placeholder: '••••••••', description: 'AppSecret trên nền tảng WeChat MP', default_value: '' }
    ]
  },
  {
    type: 'weixin_oc',
    display_name: 'WeChat Personal / OpenClaw (微信个人号)',
    category: 'WeChat Ecosystem',
    description: 'Kết nối tài khoản WeChat cá nhân/tư vấn viên qua giao thức iLink Bot (hỗ trợ quét mã QR đăng nhập trực tiếp).',
    doc_url: 'https://docs.astrbot.app/platform/weixin_oc.html',
    support_unified_webhook: false,
    support_qr_login: true,
    recommended_regions: ['China', 'VIP Personal Shopper'],
    supported_events: ['PrivateMessage', 'GroupMessage'],
    default_config: { id: 'wechat_personal_vip', type: 'weixin_oc', enable: false, ilink_endpoint: 'http://127.0.0.1:8099', auto_reconnect: true },
    fields: [
      { key: 'ilink_endpoint', label: 'iLink / OpenClaw Endpoint', type: 'string', required: true, placeholder: 'http://127.0.0.1:8099', description: 'Địa chỉ dịch vụ cầu nối WeChat iLink', default_value: 'http://127.0.0.1:8099' }
    ]
  },
  {
    type: 'qq_official',
    display_name: 'QQ Official Bot (QQ官方机器人 - WebSocket)',
    category: 'QQ & OneBot Ecosystem',
    description: 'Kết nối QQ Official Bot qua WebSocket Gateway chính thức của Tencent, hỗ trợ quét QR tạo Bot nhanh.',
    doc_url: 'https://docs.astrbot.app/platform/qq_official.html',
    support_unified_webhook: false,
    support_qr_login: true,
    recommended_regions: ['China', 'Community'],
    supported_events: ['GroupMessage', 'PrivateMessage', 'GuildMessage'],
    default_config: { id: 'qq_official_ws', type: 'qq_official', enable: false, appid: '', secret: '' },
    fields: [
      { key: 'appid', label: 'QQ Bot AppID', type: 'string', required: true, placeholder: '102030...', description: 'AppID trên QQ Open Platform (q.qq.com)', default_value: '' },
      { key: 'secret', label: 'QQ Bot AppSecret', type: 'password', required: true, placeholder: '••••••••', description: 'AppSecret của QQ Bot', default_value: '' }
    ]
  },
  {
    type: 'qq_official_webhook',
    display_name: 'QQ Official Bot (Webhook Mode)',
    category: 'QQ & OneBot Ecosystem',
    description: 'Kết nối QQ Official Bot qua chế độ Unified Webhook chuẩn Ed25519 signature verification.',
    doc_url: 'https://docs.astrbot.app/platform/qq_official_webhook.html',
    support_unified_webhook: true,
    support_qr_login: false,
    recommended_regions: ['China', 'Community'],
    supported_events: ['GroupMessage', 'PrivateMessage', 'GuildMessage'],
    default_config: { id: 'qq_official_wh', type: 'qq_official_webhook', enable: false, appid: '', secret: '', unified_webhook_mode: true },
    fields: [
      { key: 'appid', label: 'QQ Bot AppID', type: 'string', required: true, placeholder: '102030...', description: 'AppID trên QQ Open Platform', default_value: '' },
      { key: 'secret', label: 'QQ Bot AppSecret', type: 'password', required: true, placeholder: '••••••••', description: 'AppSecret dùng để ký Ed25519', default_value: '' }
    ]
  },
  {
    type: 'aiocqhttp',
    display_name: 'OneBot v11 (NapCat / LLOneBot / Lagrange)',
    category: 'QQ & OneBot Ecosystem',
    description: 'Chuẩn giao thức mở OneBot v11 qua Reverse WebSocket (aiocqhttp), tương thích NapCat, Lagrange, LLOneBot.',
    doc_url: 'https://docs.astrbot.app/platform/aiocqhttp.html',
    support_unified_webhook: false,
    support_qr_login: false,
    recommended_regions: ['Global', 'Self-Hosted'],
    supported_events: ['GroupMessage', 'PrivateMessage', 'NoticeEvent'],
    default_config: { id: 'onebot_napcat_node', type: 'aiocqhttp', enable: true, ws_reverse_host: '0.0.0.0', ws_reverse_port: 6199, ws_reverse_token: '' },
    fields: [
      { key: 'ws_reverse_host', label: 'Reverse WS Listen Host', type: 'string', required: true, placeholder: '0.0.0.0', description: 'IP lắng nghe kết nối Reverse WebSocket từ NapCat/OneBot', default_value: '0.0.0.0' },
      { key: 'ws_reverse_port', label: 'Reverse WS Listen Port', type: 'number', required: true, placeholder: '6199', description: 'Cổng WebSocket ngược (mặc định 6199)', default_value: 6199 }
    ]
  },
  {
    type: 'satori',
    display_name: 'Satori Universal Protocol',
    category: 'Open Federation & Protocol',
    description: 'Giao thức chatbot đa nền tảng Satori (chronocat, koishi, satori-python) kết nối đồng thời nhiều mạng chat.',
    doc_url: 'https://docs.astrbot.app/platform/satori.html',
    support_unified_webhook: false,
    support_qr_login: false,
    recommended_regions: ['Global', 'Self-Hosted'],
    supported_events: ['GroupMessage', 'PrivateMessage'],
    default_config: { id: 'satori_universal_hub', type: 'satori', enable: false, satori_endpoint: 'http://localhost:5140', satori_token: '' },
    fields: [
      { key: 'satori_endpoint', label: 'Satori API Endpoint', type: 'string', required: true, placeholder: 'http://localhost:5140', description: 'URL gốc của Satori Server', default_value: 'http://localhost:5140' },
      { key: 'satori_token', label: 'Satori Bearer Token', type: 'password', required: false, placeholder: '••••••••', description: 'Token xác thực Satori', default_value: '' }
    ]
  },
  {
    type: 'kook',
    display_name: 'KOOK (开黑啦)',
    category: 'Open Federation & Protocol',
    description: 'Kết nối cộng đồng âm thanh & chat KOOK qua WebSocket Gateway.',
    doc_url: 'https://docs.astrbot.app/platform/kook.html',
    support_unified_webhook: false,
    support_qr_login: false,
    recommended_regions: ['APAC', 'Gaming Community'],
    supported_events: ['GroupMessage', 'PrivateMessage'],
    default_config: { id: 'kook_community_bot', type: 'kook', enable: false, kook_bot_token: '' },
    fields: [
      { key: 'kook_bot_token', label: 'KOOK Bot Token', type: 'password', required: true, placeholder: '1/MTIzNDU=...', description: 'Token Bot trong KOOK Developer Center', default_value: '' }
    ]
  },
  {
    type: 'mattermost',
    display_name: 'Mattermost Self-Hosted',
    category: 'Enterprise Workspace',
    description: 'Kết nối nền tảng chat nội bộ mã nguồn mở Mattermost cho doanh nghiệp bảo mật cao.',
    doc_url: 'https://docs.astrbot.app/platform/mattermost.html',
    support_unified_webhook: true,
    support_qr_login: false,
    recommended_regions: ['Global', 'On-Premise Enterprise'],
    supported_events: ['GroupMessage', 'PrivateMessage'],
    default_config: { id: 'mattermost_sec_ops', type: 'mattermost', enable: false, mattermost_url: 'https://chat.company.internal', bot_access_token: '', unified_webhook_mode: true },
    fields: [
      { key: 'mattermost_url', label: 'Mattermost Server URL', type: 'string', required: true, placeholder: 'https://chat.company.internal', description: 'Địa chỉ máy chủ Mattermost của tổ chức', default_value: 'https://chat.company.internal' },
      { key: 'bot_access_token', label: 'Bot Access Token', type: 'password', required: true, placeholder: '••••••••', description: 'Personal Access Token hoặc Bot Account Token', default_value: '' }
    ]
  },
  {
    type: 'misskey',
    display_name: 'Misskey (Fediverse)',
    category: 'Open Federation & Protocol',
    description: 'Kết nối mạng xã hội phi tập trung Fediverse / ActivityPub qua Misskey WebSocket Streaming API.',
    doc_url: 'https://docs.astrbot.app/platform/misskey.html',
    support_unified_webhook: false,
    support_qr_login: false,
    recommended_regions: ['Global', 'Japan', 'Fediverse'],
    supported_events: ['PrivateMessage', 'GroupMessage', 'MentionNote'],
    default_config: { id: 'misskey_fediverse_bot', type: 'misskey', enable: false, instance_url: 'https://misskey.io', access_token: '' },
    fields: [
      { key: 'instance_url', label: 'Misskey Instance URL', type: 'string', required: true, placeholder: 'https://misskey.io', description: 'URL máy chủ Misskey', default_value: 'https://misskey.io' },
      { key: 'access_token', label: 'API Access Token (MiAuth)', type: 'password', required: true, placeholder: '••••••••', description: 'Token có quyền đọc/gửi Note và Chat', default_value: '' }
    ]
  },
  {
    type: 'facebook_messenger',
    display_name: 'Facebook Messenger (Meta Graph API)',
    category: 'Global Messaging',
    description: 'Kết nối trực tiếp với cổng Facebook Messenger Webhook & Christian Dior Luxury KB có sẵn trong DocMind.',
    doc_url: 'https://developers.facebook.com/docs/messenger-platform/webhooks',
    support_unified_webhook: true,
    support_qr_login: false,
    recommended_regions: ['Vietnam', 'Global'],
    supported_events: ['PrivateMessage', 'Postback', 'QuickReply'],
    default_config: { id: 'facebook_dior_flagship', type: 'facebook_messenger', enable: true, page_id: 'dior.vietnam.official', verify_token: 'docmind_fb_verify_token', page_access_token: '', unified_webhook_mode: true },
    fields: [
      { key: 'page_id', label: 'Facebook Page ID', type: 'string', required: true, placeholder: '10492837465...', description: 'ID Fanpage Facebook kết nối với DocMind', default_value: 'dior.vietnam.official' },
      { key: 'verify_token', label: 'Webhook Verify Token', type: 'string', required: true, placeholder: 'docmind_fb_verify_token', description: 'Chuỗi xác thực GET hub.verify_token từ Meta', default_value: 'docmind_fb_verify_token' },
      { key: 'page_access_token', label: 'Page Access Token', type: 'password', required: true, placeholder: 'EAAGm0PX4ZCps...', description: 'Token gửi tin nhắn qua Send API v19.0', default_value: '' }
    ]
  },
  {
    type: 'webchat',
    display_name: 'AstrBot & DocMind WebChat',
    category: 'Built-in Channel',
    description: 'Giao diện WebChat trực tiếp trên trình duyệt kết nối thẳng vào pipeline RAG + Dior KB + EvoAgentX.',
    doc_url: 'https://docs.astrbot.app',
    support_unified_webhook: false,
    support_qr_login: false,
    recommended_regions: ['Built-in', 'Global'],
    supported_events: ['PrivateMessage', 'GroupMessage', 'Command'],
    default_config: { id: 'webchat_embedded_portal', type: 'webchat', enable: true, allow_guest_session: true, streaming_response: true },
    fields: [
      { key: 'allow_guest_session', label: 'Cho phép khách vãng lai chat không cần đăng nhập', type: 'boolean', required: false, placeholder: '', description: 'Tự động cấp phát session_id cho khách hàng mới trên web', default_value: true },
      { key: 'streaming_response', label: 'Bật phản hồi trực tiếp (Streaming)', type: 'boolean', required: false, placeholder: '', description: 'Truyền luồng câu trả lời theo thời gian thực', default_value: true }
    ]
  }
]

export const mockAstrBotState = {
  enabled: true,
  version: 'v4.14.0-docmind',
  timezone: 'Asia/Ho_Chi_Minh',
  webhook_base_path: '/api/astrbot/webhooks/platforms',
  bots: [
    {
      id: 'telegram_dior_concierge',
      type: 'telegram',
      display_name: 'Telegram Bot',
      enable: true,
      status: 'running',
      started_at: '2026-10-15T01:00:00Z',
      unified_webhook: true,
      webhook_uuid: 'wh_telegram_dior_concierge_a1b2',
      webhook_url: '/api/astrbot/webhooks/platforms/wh_telegram_dior_concierge_a1b2',
      error_count: 0,
      last_error: null,
      message_count: 46,
      active_sessions: 3,
      fallback_profile_id: 'dior_luxury_default',
      default_persona_id: 'dior_luxury_advisor',
      default_scenario: 'SALES',
      has_qr_payload: false,
      qr_status: null,
      qr_code_img_content: null,
      config: { id: 'telegram_dior_concierge', type: 'telegram', enable: true, telegram_token: '749281:AAH-dior-concierge-••••', start_with_webhook: true, unified_webhook_mode: true },
      umo_routes: [
        { umo: 'telegram_dior_concierge:PrivateMessage:vip_client_8801', config_id: 'dior_luxury_default', persona_id: 'dior_luxury_advisor', scenario: 'SALES' }
      ]
    },
    {
      id: 'discord_community_hub',
      type: 'discord',
      display_name: 'Discord Bot',
      enable: true,
      status: 'running',
      started_at: '2026-10-15T01:05:00Z',
      unified_webhook: false,
      webhook_uuid: 'wh_discord_community_hub_c3d4',
      webhook_url: '/api/astrbot/webhooks/platforms/wh_discord_community_hub_c3d4',
      error_count: 0,
      last_error: null,
      message_count: 29,
      active_sessions: 2,
      fallback_profile_id: 'dior_luxury_default',
      default_persona_id: 'docmind_rag_expert',
      default_scenario: 'SERVICE_SUPPORT',
      has_qr_payload: false,
      qr_status: null,
      qr_code_img_content: null,
      config: { id: 'discord_community_hub', type: 'discord', enable: true, discord_token: 'MTIzNDU2••••', discord_application_id: '11982736451122', bot_status_text: 'DocMind x AstrBot Hub' },
      umo_routes: []
    },
    {
      id: 'facebook_dior_flagship',
      type: 'facebook_messenger',
      display_name: 'Facebook Messenger (Meta Graph API)',
      enable: true,
      status: 'running',
      started_at: '2026-10-15T01:10:00Z',
      unified_webhook: true,
      webhook_uuid: 'wh_facebook_dior_flagship_e5f6',
      webhook_url: '/api/astrbot/webhooks/platforms/wh_facebook_dior_flagship_e5f6',
      error_count: 0,
      last_error: null,
      message_count: 68,
      active_sessions: 4,
      fallback_profile_id: 'dior_luxury_default',
      default_persona_id: 'dior_luxury_advisor',
      default_scenario: 'SALES',
      has_qr_payload: false,
      qr_status: null,
      qr_code_img_content: null,
      config: { id: 'facebook_dior_flagship', type: 'facebook_messenger', enable: true, page_id: 'dior.vietnam.official', verify_token: 'docmind_fb_verify_token', page_access_token: 'EAAGm0PX••••', unified_webhook_mode: true },
      umo_routes: []
    },
    {
      id: 'slack_enterprise_desk',
      type: 'slack',
      display_name: 'Slack App',
      enable: true,
      status: 'running',
      started_at: '2026-10-15T01:15:00Z',
      unified_webhook: true,
      webhook_uuid: 'wh_slack_enterprise_desk_g7h8',
      webhook_url: '/api/astrbot/webhooks/platforms/wh_slack_enterprise_desk_g7h8',
      error_count: 0,
      last_error: null,
      message_count: 19,
      active_sessions: 1,
      fallback_profile_id: 'dior_luxury_default',
      default_persona_id: 'docmind_rag_expert',
      default_scenario: 'SERVICE_SUPPORT',
      has_qr_payload: false,
      qr_status: null,
      qr_code_img_content: null,
      config: { id: 'slack_enterprise_desk', type: 'slack', enable: true, bot_token: 'xoxb-docmind-••••', signing_secret: '8f9a2b••••', connection_mode: 'webhook', unified_webhook_mode: true },
      umo_routes: []
    },
    {
      id: 'lark_feishu_workspace',
      type: 'lark',
      display_name: 'Lark / Feishu (飞书)',
      enable: true,
      status: 'running',
      started_at: '2026-10-15T01:20:00Z',
      unified_webhook: true,
      webhook_uuid: 'wh_lark_feishu_workspace_i9j0',
      webhook_url: '/api/astrbot/webhooks/platforms/wh_lark_feishu_workspace_i9j0',
      error_count: 0,
      last_error: null,
      message_count: 24,
      active_sessions: 1,
      fallback_profile_id: 'dior_luxury_default',
      default_persona_id: 'vip_aftersales_concierge',
      default_scenario: 'WARRANTY_RETURN',
      has_qr_payload: false,
      qr_status: null,
      qr_code_img_content: null,
      config: { id: 'lark_feishu_workspace', type: 'lark', enable: true, app_id: 'cli_a58fdior99', app_secret: 'lark-secret-••••', domain: 'https://open.larksuite.com' },
      umo_routes: []
    },
    {
      id: 'line_official_concierge',
      type: 'line',
      display_name: 'LINE Messaging API',
      enable: true,
      status: 'running',
      started_at: '2026-10-15T01:25:00Z',
      unified_webhook: true,
      webhook_uuid: 'wh_line_official_concierge_k1l2',
      webhook_url: '/api/astrbot/webhooks/platforms/wh_line_official_concierge_k1l2',
      error_count: 0,
      last_error: null,
      message_count: 15,
      active_sessions: 1,
      fallback_profile_id: 'dior_luxury_default',
      default_persona_id: 'dior_luxury_advisor',
      default_scenario: 'SALES',
      has_qr_payload: false,
      qr_status: null,
      qr_code_img_content: null,
      config: { id: 'line_official_concierge', type: 'line', enable: true, channel_access_token: 'eyJhbGci••••', channel_secret: '3f8a9c••••', unified_webhook_mode: true },
      umo_routes: []
    },
    {
      id: 'onebot_napcat_node',
      type: 'aiocqhttp',
      display_name: 'OneBot v11 (NapCat / LLOneBot / Lagrange)',
      enable: false,
      status: 'stopped',
      started_at: null,
      unified_webhook: false,
      webhook_uuid: 'wh_onebot_napcat_node_m3n4',
      webhook_url: '/api/astrbot/webhooks/platforms/wh_onebot_napcat_node_m3n4',
      error_count: 0,
      last_error: null,
      message_count: 7,
      active_sessions: 0,
      fallback_profile_id: 'dior_luxury_default',
      default_persona_id: 'docmind_rag_expert',
      default_scenario: 'AUTO',
      has_qr_payload: false,
      qr_status: null,
      qr_code_img_content: null,
      config: { id: 'onebot_napcat_node', type: 'aiocqhttp', enable: false, ws_reverse_host: '0.0.0.0', ws_reverse_port: 6199 },
      umo_routes: []
    },
    {
      id: 'webchat_embedded_portal',
      type: 'webchat',
      display_name: 'AstrBot & DocMind WebChat',
      enable: true,
      status: 'running',
      started_at: '2026-10-15T01:30:00Z',
      unified_webhook: false,
      webhook_uuid: 'wh_webchat_embedded_portal_o5p6',
      webhook_url: '/api/astrbot/webhooks/platforms/wh_webchat_embedded_portal_o5p6',
      error_count: 0,
      last_error: null,
      message_count: 52,
      active_sessions: 2,
      fallback_profile_id: 'dior_luxury_default',
      default_persona_id: 'dior_luxury_advisor',
      default_scenario: 'AUTO',
      has_qr_payload: false,
      qr_status: null,
      qr_code_img_content: null,
      config: { id: 'webchat_embedded_portal', type: 'webchat', enable: true, allow_guest_session: true, streaming_response: true },
      umo_routes: []
    }
  ],
  sessions: [
    {
      umo: 'telegram_dior_concierge:PrivateMessage:vip_client_8801',
      platform_id: 'telegram_dior_concierge',
      platform_type: 'telegram',
      message_type: 'PrivateMessage',
      session_id: 'vip_client_8801',
      sender_id: 'vip_client_8801',
      sender_name: 'Chị Minh Thư (VIP Diamond)',
      custom_name: 'VIP Minh Thư · Dior Skincare',
      llm_enabled: true,
      tts_enabled: false,
      persona_id: 'dior_luxury_advisor',
      scenario: 'SALES',
      config_profile_id: 'dior_luxury_default',
      kb_names: ['dior_sop_kb', 'docmind_hybrid_rag'],
      disabled_plugins: [],
      message_count: 2,
      last_message: 'Da mình hơi khô vùng chữ U, muốn tìm hiểu dòng Dior Prestige La Micro-Huile de Rose.',
      last_reply: 'Dạ Christian Dior xin kính chào Chị Minh Thư ạ! Với làn da khô vùng chữ U, tinh chất vi điểm **Dior Prestige La Micro-Huile de Rose Advanced Serum** là lựa chọn hoàn hảo nhất ạ.',
      last_active_at: '2026-10-15T03:45:00Z',
      history: [
        { role: 'user', content: 'Da mình hơi khô vùng chữ U, muốn tìm hiểu dòng Dior Prestige La Micro-Huile de Rose.' },
        { role: 'assistant', content: 'Dạ Christian Dior xin kính chào Chị Minh Thư ạ! Với làn da khô vùng chữ U, tinh chất vi điểm **Dior Prestige La Micro-Huile de Rose Advanced Serum** là lựa chọn hoàn hảo nhất ạ.' }
      ]
    },
    {
      umo: 'discord_community_hub:GroupMessage:channel_rag_architecture',
      platform_id: 'discord_community_hub',
      platform_type: 'discord',
      message_type: 'GroupMessage',
      session_id: 'channel_rag_architecture',
      sender_id: 'dev_lead_42',
      sender_name: 'Hoàng Nam (AI Architect)',
      custom_name: '#rag-architecture · Discord Guild',
      llm_enabled: true,
      tts_enabled: false,
      persona_id: 'docmind_rag_expert',
      scenario: 'SERVICE_SUPPORT',
      config_profile_id: 'dior_luxury_default',
      kb_names: ['docmind_hybrid_rag'],
      disabled_plugins: [],
      message_count: 2,
      last_message: 'Cơ chế Reciprocal RankFusion (RRF) trong DocMind kết hợp Vector và FTS như thế nào?',
      last_reply: 'Trong kiến trúc Hybrid RAG của DocMind, **Reciprocal Rank Fusion (RRF, k=60)** hợp nhất bảng xếp hạng từ pgvector HNSW và PostgreSQL Full-Text Search để tối ưu độ chính xác truy xuất.',
      last_active_at: '2026-10-15T03:30:00Z',
      history: []
    },
    {
      umo: 'lark_feishu_workspace:PrivateMessage:lark_exec_09',
      platform_id: 'lark_feishu_workspace',
      platform_type: 'lark',
      message_type: 'PrivateMessage',
      session_id: 'lark_exec_09',
      sender_id: 'lark_exec_09',
      sender_name: 'Trần Bảo Ngọc (Lark Enterprise)',
      custom_name: 'CSKH Hậu mãi · Lark VIP',
      llm_enabled: true,
      tts_enabled: false,
      persona_id: 'vip_aftersales_concierge',
      scenario: 'WARRANTY_RETURN',
      config_profile_id: 'dior_luxury_default',
      kb_names: ['dior_sop_kb'],
      disabled_plugins: [],
      message_count: 2,
      last_message: 'Mình nhận đơn nước hoa J\'adore hôm qua nhưng hộp quà bị móp góc do vận chuyển, hỗ trợ đổi hộp giúp mình nhé.',
      last_reply: 'Dạ Christian Dior thành thật xin lỗi Chị Bảo Ngọc vì trải nghiệm chưa trọn vẹn này ạ! Em đã khởi tạo phiếu ưu tiên **Đổi mới Hộp quà Couture miễn phí 2 chiều** ngay lập tức ạ.',
      last_active_at: '2026-10-15T03:15:00Z',
      history: []
    }
  ],
  personas: [
    {
      id: 'dior_luxury_advisor',
      name: 'Christian Dior Luxury Beauty & Couture Advisor',
      folder: 'Luxury Retail',
      system_prompt: 'Bạn là Chuyên viên Tư vấn Cao cấp chính thức của thương hiệu Christian Dior (Parfums & Couture). Luôn xưng hô trang nhã (Dạ / Quý khách), chẩn đoán nhu cầu tinh tế theo SOP 5 bước của Dior, trích dẫn chính xác thành phần, nốt hương, giá tham khảo và chính sách gói quà Art of Gifting.',
      begin_dialogs: [
        'Xin chào, tư vấn giúp mình sản phẩm Dior phù hợp nhé!',
        'Dạ Christian Dior xin kính chào Quý khách! Em rất hân hạnh được đồng hành cùng Quý khách lựa chọn tuyệt tác Nước hoa, Trang điểm, Dưỡng da và Thời trang Dior phù hợp nhất ạ.'
      ],
      tools: ['dior_product_kb_search', 'rag_hybrid_search', 'evoagentx_guideline_lookup', 'order_gift_check'],
      temperature: 0.3,
      default_scenario: 'SALES',
      updated_at: '2026-10-15T01:00:00Z'
    },
    {
      id: 'docmind_rag_expert',
      name: 'DocMind Technical RAG & Document Analyst',
      folder: 'Enterprise RAG',
      system_prompt: 'Bạn là Kỹ sư Phân tích Tài liệu & Trợ lý Tri thức RAG trên hệ thống DocMind. Mọi câu trả lời phải bám sát văn bản nguồn được truy xuất qua Hybrid Search (pgvector + FTS + RRF), đính kèm trích dẫn [1], [2] rõ ràng.',
      begin_dialogs: [],
      tools: ['rag_hybrid_search', 'pdf_page_locator', 'citation_verifier'],
      temperature: 0.2,
      default_scenario: 'SERVICE_SUPPORT',
      updated_at: '2026-10-15T01:00:00Z'
    },
    {
      id: 'vip_aftersales_concierge',
      name: 'VIP After-Sales & Warranty Concierge',
      folder: 'Customer Care',
      system_prompt: 'Bạn là Chuyên viên Chăm sóc Hậu mãi & Bảo hành Đổi trả VIP. Luôn thể hiện sự thấu cảm sâu sắc ngay câu đầu tiên, nắm vững quy trình đổi trả 7 ngày, kiểm định批号 (batch code) chính hãng và xử lý khiếu nại vận chuyển hoả tốc.',
      begin_dialogs: [],
      tools: ['warranty_policy_lookup', 'batch_code_authenticator', 'create_return_ticket'],
      temperature: 0.25,
      default_scenario: 'WARRANTY_RETURN',
      updated_at: '2026-10-15T01:00:00Z'
    }
  ],
  providers: [
    {
      id: '9router-codex-astra-primary',
      provider: '9Router OpenAI-Compatible Proxy',
      type: 'openai_chat_completion',
      provider_type: 'chat_completion',
      enable: true,
      api_base: 'http://localhost:20128/v1',
      model: 'codex/gpt-6-astra',
      models: ['codex/gpt-6-astra', 'gpt-5.4', 'claude-3-7-sonnet'],
      timeout: 120,
      key_configured: true,
      masked_key: 'sk-9router-••••••a9f',
      status: 'connected',
      latency_ms: 42
    },
    {
      id: 'google-gemini-rag-fallback',
      provider: 'Google Gemini API',
      type: 'googlegenai_chat_completion',
      provider_type: 'chat_completion',
      enable: true,
      api_base: 'https://generativelanguage.googleapis.com/v1beta',
      model: 'gemini-2.5-flash',
      models: ['gemini-2.5-flash', 'gemini-2.5-pro'],
      timeout: 90,
      key_configured: true,
      masked_key: 'AIzaSy••••••8kQ',
      status: 'connected',
      latency_ms: 58
    },
    {
      id: 'docmind-multilingual-embedding',
      provider: 'DocMind ONNX / Gemini Embedding',
      type: 'embedding_provider',
      provider_type: 'embedding',
      enable: true,
      api_base: 'local://onnx-runtime-768d',
      model: 'paraphrase-multilingual-mpnet-base-v2',
      models: ['paraphrase-multilingual-mpnet-base-v2', 'text-embedding-004'],
      timeout: 30,
      key_configured: true,
      masked_key: 'internal-onnx-••••',
      status: 'connected',
      latency_ms: 11
    }
  ],
  cron_jobs: [
    {
      job_id: 'cron-dior-vip-followup',
      name: 'Chăm sóc khách VIP sau mua 7 ngày (Dior Skincare)',
      description: 'Tự động gửi tin nhắn hỏi thăm trải nghiệm sử dụng serum/kem dưỡng Dior Prestige và nhắc lịch tái khám da.',
      cron_expression: '0 9 * * *',
      target_umo: 'telegram_dior_concierge:PrivateMessage:vip_client_8801',
      prompt: 'Hãy soạn lời chào buổi sáng tinh tế gửi chị Minh Thư để hỏi thăm cảm nhận sau 7 ngày dùng Dior Prestige La Micro-Huile de Rose.',
      scenario: 'SERVICE_SUPPORT',
      run_once: false,
      enabled: true,
      last_run_at: '2026-10-15T02:00:00Z',
      next_run_at: '2026-10-16T02:00:00Z',
      run_count: 5,
      last_status: 'succeeded',
      last_result_preview: 'Dạ Christian Dior kính chúc Chị Minh Thư ngày mới rạng rỡ! Qua 7 ngày trải nghiệm tinh chất Dior Prestige...'
    },
    {
      job_id: 'cron-evoagentx-nightly-consolidate',
      name: 'Đồng bộ & Cắt tỉa Bộ nhớ EvoAgentX Hàng đêm',
      description: 'Thực thi chu kỳ phản tư TextGrad + MIPRO trên toàn bộ các phiên chat đa nền tảng trong ngày.',
      cron_expression: '30 23 * * *',
      target_umo: 'slack_enterprise_desk:GroupMessage:c_ai_ops_report',
      prompt: 'Tổng hợp báo cáo chất lượng phản hồi đa nền tảng và kích hoạt chu kỳ tự tiến hoá EvoAgentX.',
      scenario: 'GENERAL',
      run_once: false,
      enabled: true,
      last_run_at: '2026-10-14T16:30:00Z',
      next_run_at: '2026-10-15T16:30:00Z',
      run_count: 14,
      last_status: 'succeeded',
      last_result_preview: 'Đã hoàn tất chu kỳ tiến hoá Gen #4: cập nhật 16 quy tắc TextGrad, độ hài lòng trung bình đạt 94.2%.'
    }
  ],
  extensions: [
    {
      id: 'star-docmind-rag-bridge',
      name: 'astrbot_plugin_docmind_rag',
      display_name: 'DocMind Hybrid RAG & PDF Citation Bridge',
      kind: 'star_plugin',
      author: 'DocMind Core Team',
      version: '2.4.0',
      description: 'Kết nối đường ống sự kiện AstrBot với kho tài liệu PDF Hybrid Search (pgvector + FTS + RRF) và Dior Luxury Knowledge Base.',
      enabled: true,
      commands: ['/kb', '/search', '/cite', '/doc'],
      tools_count: 4,
      endpoint: 'internal://docmind.retrieval.service'
    },
    {
      id: 'star-evoagentx-optimizer',
      name: 'astrbot_plugin_evoagentx',
      display_name: 'EvoAgentX Self-Evolving Prompt Optimizer (TextGrad + MIPRO)',
      kind: 'star_plugin',
      author: 'DocMind x EvoAgentX',
      version: '1.8.2',
      description: 'Tự động chấm điểm Loss Evaluation, trích xuất Textual Gradient và chọn lọc Few-Shot Exemplars sau mỗi lượt chat đa kênh.',
      enabled: true,
      commands: ['/evolve', '/gradients', '/guidelines'],
      tools_count: 3,
      endpoint: 'internal://docmind.evolution.service'
    },
    {
      id: 'mcp-dior-catalog-server',
      name: 'mcp_server_dior_catalog',
      display_name: 'Christian Dior Product & SOP MCP Server',
      kind: 'mcp_server',
      author: 'Christian Dior Maison Digital',
      version: '3.1.0',
      description: 'Máy chủ Model Context Protocol (MCP) cung cấp công cụ tra cứu danh mục Nước hoa, Trang điểm, Dưỡng da, Túi xách Dior và kiểm tra tồn kho Boutique.',
      enabled: true,
      commands: ['/dior', '/shade', '/scent'],
      tools_count: 6,
      endpoint: 'mcp://local/dior-luxury-kb-2026'
    },
    {
      id: 'mcp-codesearch-tasktracker',
      name: 'mcp_server_ops_toolkit',
      display_name: 'Codesearch & Task Tracker MCP Bridge',
      kind: 'mcp_server',
      author: 'Antigravity Engineering',
      version: '1.2.0',
      description: 'Tích hợp công cụ truy xuất mã nguồn tốc độ cao Codesearch BM25 và quản lý công việc Task Tracker ngay trong cuộc hội thoại kỹ thuật.',
      enabled: true,
      commands: ['/task', '/codesearch'],
      tools_count: 5,
      endpoint: 'mcp://local/codesearch-task-tracker'
    }
  ],
  traces: [
    {
      trace_id: 'astr-init-01',
      timestamp: '2026-10-15T03:45:00Z',
      umo: 'telegram_dior_concierge:PrivateMessage:vip_client_8801',
      platform_id: 'telegram_dior_concierge',
      platform_type: 'telegram',
      message_type: 'PrivateMessage',
      sender_id: 'vip_client_8801',
      sender_name: 'Chị Minh Thư (VIP Diamond)',
      question: 'Da mình hơi khô vùng chữ U, muốn tìm hiểu dòng Dior Prestige La Micro-Huile de Rose.',
      reply_excerpt: 'Dạ Christian Dior xin kính chào Chị Minh Thư ạ! Với làn da khô vùng chữ U, tinh chất vi điểm Dior Prestige...',
      scenario: 'SALES',
      persona_id: 'dior_luxury_advisor',
      citations_count: 2,
      evo_quality_score: 0.96,
      evo_generation: 4,
      latency_ms: 48,
      is_command: false,
      status: 'succeeded'
    },
    {
      trace_id: 'astr-init-02',
      timestamp: '2026-10-15T03:30:00Z',
      umo: 'discord_community_hub:GroupMessage:channel_rag_architecture',
      platform_id: 'discord_community_hub',
      platform_type: 'discord',
      message_type: 'GroupMessage',
      sender_id: 'dev_lead_42',
      sender_name: 'Hoàng Nam (AI Architect)',
      question: 'Cơ chế Reciprocal RankFusion (RRF) trong DocMind kết hợp Vector và FTS như thế nào?',
      reply_excerpt: 'Trong kiến trúc Hybrid RAG của DocMind, Reciprocal Rank Fusion (RRF, k=60) hợp nhất bảng xếp hạng...',
      scenario: 'SERVICE_SUPPORT',
      persona_id: 'docmind_rag_expert',
      citations_count: 1,
      evo_quality_score: 0.94,
      evo_generation: 4,
      latency_ms: 35,
      is_command: false,
      status: 'succeeded'
    }
  ],
  logs: [
    {
      id: 'log-101',
      timestamp: '2026-10-15T03:45:00Z',
      level: 'INFO',
      platform_id: 'telegram_dior_concierge',
      source: 'EventPipeline',
      message: 'Đã xử lý thành công UMO [telegram_dior_concierge:PrivateMessage:vip_client_8801] qua kịch bản SALES (Persona: dior_luxury_advisor, EvoGen #4).',
      is_user_chat: true
    },
    {
      id: 'log-100',
      timestamp: '2026-10-15T01:30:00Z',
      level: 'INFO',
      platform_id: 'system',
      source: 'PlatformManager',
      message: 'Đã nạp 19 Platform Adapter schemas (Telegram, Discord, Slack, Lark, DingTalk, LINE, WeCom, WeChat, QQ, OneBot v11, Satori, KOOK, Mattermost, Misskey, Facebook Messenger, WebChat).',
      is_user_chat: false
    }
  ]
}

export function getMockAstrBotOverview() {
  const bots = mockAstrBotState.bots
  const runningBots = bots.filter((b) => b.status === 'running').length
  const stoppedBots = bots.filter((b) => b.status === 'stopped').length
  const errorBots = bots.filter((b) => b.status === 'error').length
  const totalMessages = bots.reduce((acc, b) => acc + (b.message_count || 0), 0)
  const ranking = [...bots]
    .map((b) => ({ id: b.id, type: b.type, name: b.display_name, status: b.status, count: b.message_count || 0 }))
    .sort((a, b) => b.count - a.count)

  return JSON.parse(
    JSON.stringify({
      enabled: mockAstrBotState.enabled,
      version: mockAstrBotState.version,
      timezone: mockAstrBotState.timezone,
      webhook_base_path: mockAstrBotState.webhook_base_path,
      stats: {
        total_messages: totalMessages,
        total_bots: bots.length,
        running_bots: runningBots,
        stopped_bots: stoppedBots,
        error_bots: errorBots,
        total_errors: 0,
        supported_platform_types: mockAstrBotPlatformTypes.length,
        active_sessions: mockAstrBotState.sessions.length,
        personas_count: mockAstrBotState.personas.length,
        providers_count: mockAstrBotState.providers.length,
        cron_jobs_count: mockAstrBotState.cron_jobs.length,
        extensions_count: mockAstrBotState.extensions.length,
        avg_latency_ms: 42,
        evolution_generation: mockEvolutionData.evolution_generation,
        evolution_quality_score: mockEvolutionData.average_quality_score,
        platform_ranking: ranking
      },
      bot_types: mockAstrBotPlatformTypes,
      bots: mockAstrBotState.bots,
      sessions: mockAstrBotState.sessions,
      personas: mockAstrBotState.personas,
      providers: mockAstrBotState.providers,
      cron_jobs: mockAstrBotState.cron_jobs,
      extensions: mockAstrBotState.extensions,
      traces: mockAstrBotState.traces,
      logs: mockAstrBotState.logs
    })
  )
}

export function mockSimulateAstrBotMessage(payload = {}) {
  const text = (payload.message || 'Xin chào, tư vấn sản phẩm Dior giúp mình').trim()
  const botId = payload.bot_id || 'telegram_dior_concierge'
  const bot = mockAstrBotState.bots.find((b) => b.id === botId) || mockAstrBotState.bots[0]
  const msgType = payload.message_type || 'PrivateMessage'
  const senderId = payload.sender_id || 'vip_client_8801'
  const sessionId = payload.session_id || senderId
  const senderName = payload.sender_name || 'Khách hàng VIP'
  const umo = `${bot.id}:${msgType}:${sessionId}`
  const personaId = payload.persona_id || bot.default_persona_id || 'dior_luxury_advisor'
  const scenario = payload.scenario && payload.scenario !== 'AUTO' ? payload.scenario : bot.default_scenario || 'SALES'

  const isCommand = text.startsWith('/')
  let replySent = ''
  let citations = []
  let quickReplies = ['✨ Tư vấn nước hoa Dior', '💄 Bảng màu Rouge Dior 999', '🛡️ Chính sách đổi trả', '/status']

  if (isCommand) {
    const cmd = text.split(/\s+/)[0].toLowerCase()
    if (cmd === '/help' || cmd === '/start') {
      replySent = `🤖 **Trung tâm Điều khiển Đa Nền tảng DocMind x AstrBot**\n- **Nền tảng**: \`${bot.display_name}\` (\`${bot.id}\`)\n- **Phiên UMO**: \`${umo}\`\n- **Các lệnh hỗ trợ**: \`/help\`, \`/status\`, \`/reset\`, \`/persona <id>\`, \`/scenario <mã>\`, \`/evolve\`, \`/whoami\``
    } else if (cmd === '/status') {
      replySent = `📊 **Trạng thái Hệ thống AstrBot x DocMind**\n- **Bot Adapter**: \`${bot.id}\` (RUNNING)\n- **Phiên UMO**: \`${umo}\`\n- **Persona**: \`${personaId}\` · **Kịch bản**: \`${scenario}\`\n- **EvoAgentX**: Thế hệ \`Gen #${mockEvolutionData.evolution_generation}\` · Điểm chất lượng \`${(mockEvolutionData.average_quality_score * 100).toFixed(1)}%\``
    } else if (cmd === '/reset' || cmd === '/clear') {
      replySent = `✅ Đã làm mới toàn bộ ngữ cảnh hội thoại cho phiên \`${umo}\`.`
    } else if (cmd === '/evolve') {
      mockTriggerEvolution(scenario)
      replySent = `🧬 Đã kích hoạt chu kỳ tự tiến hoá **EvoAgentX (TextGrad + MIPRO)** thành công! Thế hệ mới: **Gen #${mockEvolutionData.evolution_generation}**.`
    } else if (cmd === '/whoami' || cmd === '/sid') {
      replySent = `🪪 **Định danh Hợp nhất (UMO)**: \`${umo}\` (\`${bot.type}\` · \`${msgType}\`)`
    } else {
      replySent = `✅ AstrBot đã thực thi lệnh \`${text}\` trên phiên \`${umo}\`.`
    }
  } else {
    const simChat = mockSimulateReply(senderId, text, scenario)
    replySent = `Dạ Christian Dior & DocMind [${bot.display_name}] xin kính chào ${senderName}!\n\n${simChat.reply_sent}`
    citations = [
      {
        document_id: 'doc-dior-sop-01',
        filename: 'Christian_Dior_Luxury_Clienteling_SOP.pdf',
        page_number: 1,
        snippet: 'Chẩn đoán nhu cầu cá nhân hoá & đề xuất tuyệt tác Dior.'
      }
    ]
    quickReplies = (simChat.quick_replies || []).slice(0, 4)
  }

  bot.message_count = (bot.message_count || 0) + 1
  const nowIso = new Date().toISOString()

  let sess = mockAstrBotState.sessions.find((s) => s.umo === umo)
  if (!sess) {
    sess = {
      umo,
      platform_id: bot.id,
      platform_type: bot.type,
      message_type: msgType,
      session_id: sessionId,
      sender_id: senderId,
      sender_name: senderName,
      custom_name: `${senderName} · ${bot.display_name}`,
      llm_enabled: true,
      tts_enabled: false,
      persona_id: personaId,
      scenario,
      config_profile_id: 'dior_luxury_default',
      kb_names: ['dior_sop_kb', 'docmind_hybrid_rag'],
      disabled_plugins: [],
      message_count: 0,
      last_message: '',
      last_reply: '',
      last_active_at: nowIso,
      history: []
    }
    mockAstrBotState.sessions.unshift(sess)
  }
  sess.message_count += 1
  sess.last_message = text
  sess.last_reply = replySent
  sess.last_active_at = nowIso
  sess.history.push({ role: 'user', content: text }, { role: 'assistant', content: replySent })

  const trace = {
    trace_id: `astr-sim-${Date.now().toString(36)}`,
    timestamp: nowIso,
    umo,
    platform_id: bot.id,
    platform_type: bot.type,
    message_type: msgType,
    sender_id: senderId,
    sender_name: senderName,
    question: text,
    reply_excerpt: replySent.slice(0, 180),
    scenario,
    persona_id: personaId,
    citations_count: citations.length,
    evo_quality_score: mockEvolutionData.average_quality_score,
    evo_generation: mockEvolutionData.evolution_generation,
    latency_ms: 39,
    is_command: isCommand,
    status: 'succeeded'
  }
  mockAstrBotState.traces.unshift(trace)
  mockAstrBotState.logs.unshift({
    id: `log-${Date.now()}`,
    timestamp: nowIso,
    level: 'INFO',
    platform_id: bot.id,
    source: 'EventPipeline',
    message: `[${umo}] ${senderName}: ${text} -> Đã phản hồi (${scenario}, persona=${personaId}).`,
    is_user_chat: true
  })

  return {
    interaction_id: trace.trace_id,
    umo,
    platform_id: bot.id,
    platform_type: bot.type,
    message_type: msgType,
    sender_id: senderId,
    sender_name: senderName,
    incoming_message: text,
    reply_sent: replySent,
    scenario,
    persona_id: personaId,
    profile_id: 'dior_luxury_default',
    is_command: isCommand,
    citations,
    quick_replies: quickReplies,
    evo_generation: mockEvolutionData.evolution_generation,
    evo_quality_score: mockEvolutionData.average_quality_score,
    latency_ms: 39,
    live_delivery_attempted: true,
    timestamp: nowIso
  }
}

