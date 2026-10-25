package com.maximegrau.brin.model.user;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LoginTicketRepository extends JpaRepository<LoginTicket, String> {
}
