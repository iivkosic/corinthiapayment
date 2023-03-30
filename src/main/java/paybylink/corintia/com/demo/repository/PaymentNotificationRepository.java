package paybylink.corintia.com.demo.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import paybylink.corintia.com.demo.model.PaymentNotification;

import java.util.List;

public interface PaymentNotificationRepository extends JpaRepository<PaymentNotification, Long> {

    PaymentNotification findByMerchantReference(String merchantReference);
    List<PaymentNotification> findByStatusAndEventCodeAndPblGenerated(String status, String eventCode, Boolean pblGenerated);

}
