package paybylink.corintia.com.demo.service;

import org.springframework.stereotype.Service;
import paybylink.corintia.com.demo.model.PaymentNotification;
import paybylink.corintia.com.demo.repository.PaymentNotificationRepository;

import java.util.List;

@Service
public class PaymentNotificationServiceImpl {

    private final PaymentNotificationRepository paymentNotificationRepository;

    public PaymentNotificationServiceImpl(PaymentNotificationRepository paymentNotificationRepository) {
        this.paymentNotificationRepository = paymentNotificationRepository;
    }

    public void savePaymentNotification(PaymentNotification p) {paymentNotificationRepository.save(p);}

    public PaymentNotification findById(Long id) {return paymentNotificationRepository.findById(id).get();};

    public List<PaymentNotification> findAll() {return paymentNotificationRepository.findAll();}

    public List<PaymentNotification> findByStatusAndEventCodeAndPblGenerated(String status, String eventCode, Boolean pblGenerated) {return paymentNotificationRepository.findByStatusAndEventCodeAndPblGenerated(status, eventCode, pblGenerated);}

    public PaymentNotification findByMerchantReference(String merchantReference) {return paymentNotificationRepository.findByMerchantReference(merchantReference);}

}
