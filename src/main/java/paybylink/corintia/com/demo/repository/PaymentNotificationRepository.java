package paybylink.corintia.com.demo.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import paybylink.corintia.com.demo.model.PaymentNotification;

public interface PaymentNotificationRepository extends JpaRepository<PaymentNotification, Long> {

    PaymentNotification findByMerchantReference(String merchantReference);

}
