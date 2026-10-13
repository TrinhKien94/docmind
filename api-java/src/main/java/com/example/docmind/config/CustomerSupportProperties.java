package com.example.docmind.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Cấu hình cho API tư vấn & chăm sóc khách hàng sử dụng prompt được huấn luyện trước
 * (người dùng đóng vai trò khách hàng mua hàng hoặc sử dụng dịch vụ).
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "docmind.customer-support")
public class CustomerSupportProperties {

    /** Tên thương hiệu / cửa hàng / đơn vị cung cấp dịch vụ. */
    private String storeName = "DocMind Store & Digital Services";

    /** Số hotline CSKH. */
    private String hotline = "1900-6868";

    /** Email hỗ trợ khách hàng. */
    private String supportEmail = "cskh@docmind.vn";

    /** Khung giờ làm việc. */
    private String workingHours = "08:00 - 22:00 (Thứ 2 - Chủ Nhật)";

    /** Số chunk tài liệu tối đa lấy từ RAG mặc định. */
    private int defaultTopK = 5;

    /**
     * Tự động tìm kiếm trên tất cả tài liệu trạng thái READY nếu khách hàng không truyền document_ids.
     */
    private boolean autoDiscoverDocuments = true;

    /** Số lượng tài liệu READY tối đa tự động quét khi không truyền document_ids. */
    private int maxAutoDiscoverDocuments = 20;

    /** Số lượt hội thoại gần nhất tối đa đưa vào <CONVERSATION_HISTORY> để tránh tràn context window. */
    private int maxHistoryTurns = 10;

    /** Mã định danh bộ prompt được huấn luyện trước. */
    private String promptTemplateId = "customer-sales-service-v1";

    /**
     * Chỉ thị huấn luyện bổ sung tuỳ chỉnh (nếu có). Khi để trống sẽ dùng bộ prompt
     * huấn luyện sẵn mặc định cho nghiệp vụ mua hàng & sử dụng dịch vụ.
     */
    private String customSystemPrompt = "";

    /**
     * Tri thức nền mặc định về chính sách mua hàng, gói dịch vụ, bảo hành & đổi trả
     * để hỗ trợ khách hàng ngay cả khi chưa chỉ định tài liệu cụ thể.
     */
    private String defaultPolicyContext = """
            - Chính sách mua hàng & đặt hàng: Hỗ trợ đặt hàng trực tuyến 24/7, xác nhận đơn tự động qua SMS/Email, tư vấn chọn sản phẩm và gói dịch vụ theo nhu cầu thực tế.
            - Các gói dịch vụ tiêu chuẩn:
              + Gói Basic (Cơ bản): Phù hợp cá nhân trải nghiệm, hỗ trợ xử lý tài liệu chuẩn và tra cứu cơ bản.
              + Gói Pro (Chuyên nghiệp): Phù hợp nhóm làm việc/doanh nghiệp vừa, tốc độ ưu tiên, OCR đa ngôn ngữ và tư vấn chuyên sâu.
              + Gói Enterprise (Doanh nghiệp): Tùy biến theo yêu cầu, SLA cam kết 99.9%, hỗ trợ kỹ thuật 1-1 24/7 và tích hợp API riêng.
            - Chính sách thanh toán: Hỗ trợ chuyển khoản ngân hàng (QR Pay), thẻ nội địa/quốc tế (Visa/Mastercard), ví điện tử và COD (thanh toán khi nhận hàng đối với sản phẩm vật lý). Có xuất hóa đơn VAT điện tử theo yêu cầu.
            - Chính sách vận chuyển & bàn giao: Giao hàng toàn quốc từ 1-3 ngày làm việc (nội thành 2-4 giờ); đối với dịch vụ số/phần mềm kích hoạt tài khoản ngay lập tức sau khi xác nhận thanh toán.
            - Chính sách bảo hành & đổi trả: Bảo hành chính hãng 12 tháng; hỗ trợ 1 đổi 1 hoặc hoàn tiền trong 7 ngày đầu nếu sản phẩm lỗi từ nhà sản xuất hoặc dịch vụ không đúng cam kết.""";
}
