package com.orderSystem.order_system.repository;
import com.orderSystem.order_system.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {

}
