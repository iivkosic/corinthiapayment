package paybylink.corintia.com.demo.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import paybylink.corintia.com.demo.model.MerchantMapping;

public interface MerchantMappingRepository extends JpaRepository<MerchantMapping, Long> {

    MerchantMapping findByMerchantID(String merchantID);
}
