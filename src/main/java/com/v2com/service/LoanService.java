package com.v2com.service;

import com.v2com.entity.LoanEntity;
import com.v2com.entity.ReservationEntity;
import com.v2com.entity.UserEntity;
import com.v2com.entity.enums.LoanStatus;
import com.v2com.entity.enums.ReservationStatus;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.v2com.dto.LoanDTO;
import com.v2com.dto.ReservationDTO;
import com.v2com.entity.BookEntity;
import com.v2com.repository.LoanRepository;
import com.v2com.repository.UserRepository;
import com.v2com.repository.BookRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class LoanService {

    private final LoanRepository loanRepository;
    private final UserRepository userRepository;
    private final BookRepository bookRepository;
    private final ReservationService reservationService;

    public LoanService(LoanRepository loanRepository, UserRepository userRepository, BookRepository bookRepository, ReservationService reservationService) {
        this.loanRepository = loanRepository;
        this.userRepository = userRepository;
        this.bookRepository = bookRepository;
        this.reservationService = reservationService;
    }

    public LoanDTO createLoan(LoanDTO loanDTO) {
        try {
            //Both user and book must exists
            UserEntity userEntity = userRepository.findById(loanDTO.getUserId());
            BookEntity bookEntity = bookRepository.findById(loanDTO.getBookId());
            UUID locateLoan = loanRepository.findLoadByBookId(loanDTO.getBookId());
            
            if (loanDTO.getUserId() == null) {
                throw new RuntimeException("You cannot loan a book without a user assigned!");
            } else if (loanDTO.getBookId() == null) {
                throw new RuntimeException("What book do you want to loan? Select at least one!");
            } else if (loanDTO.getLoanDate() == null) {
                throw new RuntimeException("When was the book loaned? Fill the date!");
            } else if (userEntity == null) {
                throw new RuntimeException("User not found!");
            } else if (bookEntity == null) {
                throw new RuntimeException("Book not found!");
            } else if (!loanDTO.getBookId().equals(locateLoan)) {
                ReservationDTO reservDTO = new ReservationDTO();
                reservDTO.setUserId(loanDTO.getUserId());
                reservDTO.setBookId(loanDTO.getBookId());

                ReservationDTO createDTO = reservationService.createReservation(reservDTO);
                throw new RuntimeException("This books is already borrowed... so, we've registered for you to borrow later! Reservation ID: " + createDTO.getReservationId());
            }

            LoanEntity loanEntity = new LoanEntity(userEntity, bookEntity, loanDTO.getLoanDate(), loanDTO.getLoanDueDate(), loanDTO.getReturnDate(), loanDTO.getLoanStatus());
            loanRepository.persist(loanEntity);
            
            loanDTO.setLoanId(loanEntity.getLoanId());
            return loanDTO;
        } catch (Exception e) {
            throw new IllegalArgumentException("Something went wrong...: " + e.getMessage());
        }
    }

    public LoanDTO getLoanById(UUID loanId) {
        try {
            LoanEntity loanEntity = loanRepository.findById(loanId);

            if(loanEntity == null){
                throw new IllegalArgumentException("Loan not registered!");
            } else {
                return new LoanDTO(loanEntity.getLoanId(), loanEntity.getUser().getUserId(), loanEntity.getBook().getBookId(), loanEntity.getLoanDate(), loanEntity.getLoanDueDate(), loanEntity.getReturnDate(), loanEntity.getLoanStatus());
            }

        } catch (Exception e){
            throw new IllegalArgumentException("Something went wrong...: " + e.getMessage());
        }
    } 

    public List<LoanEntity> getAllLoans() {
        try {
            if(loanRepository.findAll().list().isEmpty()) {
                throw new IllegalArgumentException("No loans found!");
            } else {
                return loanRepository.findAll().list();
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Something went wrong...: " + e.getMessage());
        }
    }

    public List<LoanEntity> getLoansByFilters(Map<String, String> filters){
        List<LoanEntity> loans = this.getAllLoans();

        if (loans.isEmpty()) {
            throw new IllegalArgumentException("No loans found!");
        }

        for (String key : filters.keySet()) {
            if (!key.equals("user") && !key.equals("book") && !key.equals("loanDate" ) && !key.equals("loanDueDate") && !key.equals("returnDate") && !key.equals("loanStatus")) {
                throw new IllegalArgumentException("One or more filters are invalid!");
            }
        }

        loans = filters.entrySet().stream().reduce(loans, (filteredloans, filter) -> filteredloans.stream().filter(loan -> {
                switch(filter.getKey()) {
                    case "user":
                        return loan.getUser().getUserId().toString().contains(filter.getValue());
                    case "book":
                        return loan.getBook().getBookId().toString().contains(filter.getValue());
                    case "loanDate":
                        return loan.getLoanDate().toString().contains(filter.getValue());
                    case "loanDueDate":
                        return loan.getLoanDueDate().toString().contains(filter.getValue());
                    case "returnDate":
                        return loan.getReturnDate().toString().contains(filter.getValue());
                    case "loanStatus":
                        return loan.getLoanStatus().toString().toUpperCase().contains(filter.getValue());
                    default:
                        return true;
                }
            //Collect the filtered loans into a list and combine the results of the reduction
            }).collect(Collectors.toList()), (u1, u2) -> u1);

        return loans;
    }

    public LoanDTO deleteLoan(UUID loanId) {
        LoanDTO loan = this.getLoanById(loanId);

        if(loan != null) {
            try {
                UserEntity userEntity = userRepository.findById(loan.getUserId());
                BookEntity bookEntity = bookRepository.findById(loan.getBookId());

                LoanEntity loanEntity = new LoanEntity(userEntity, bookEntity, loan.getLoanDate(), loan.getLoanDueDate(), loan.getReturnDate(), loan.getLoanStatus());
                loanEntity.setLoanId(loanId);

                loanRepository.delete(loanEntity);

                return loan;
            } catch (Exception e) {
                throw new IllegalArgumentException("Something went wrong...: " + e.getMessage());
            }
        } else {
            return loan;
        }
    }

    public LoanDTO updateLoan(UUID loanId, LoanDTO loanDTO) {
        LoanEntity loanEntity = loanRepository.findById(loanId);
        
        BookEntity bookEntityChange = bookRepository.findById(loanDTO.getBookId());
        if(bookEntityChange == null){
            throw new IllegalArgumentException("Book does not exists!");
        }

        UserEntity userEntityChange = userRepository.findById(loanDTO.getUserId());
        if(userEntityChange == null){
            throw new IllegalArgumentException("User does not exists!");
        }

        if(loanEntity != null){
            try {
                loanEntity.setUser(userEntityChange != null ? userEntityChange : loanEntity.getUser());
                loanEntity.setBook(bookEntityChange != null ? bookEntityChange : loanEntity.getBook());
                loanEntity.setLoanDate(loanDTO.getLoanDate() != null ? loanDTO.getLoanDate() : loanEntity.getLoanDate());
                loanEntity.setLoanDueDate(loanDTO.getLoanDueDate() != null ? loanDTO.getLoanDueDate() : loanEntity.getLoanDueDate());
                loanEntity.setReturnDate(loanDTO.getReturnDate() != null ? loanDTO.getReturnDate() : loanEntity.getReturnDate());

                if(loanEntity.getReturnDate().equals(new java.sql.Date(System.currentTimeMillis())) || loanEntity.getReturnDate().before(loanEntity.getLoanDueDate())){
                    loanEntity.setLoanStatus(LoanStatus.RETURNED);
                } else if (loanEntity.getReturnDate().after(loanEntity.getLoanDueDate()) || (loanEntity.getReturnDate() == null && loanEntity.getLoanDueDate().after(new java.sql.Date(System.currentTimeMillis())))) {
                    loanEntity.setLoanStatus(LoanStatus.LATE);
                } else {
                    loanEntity.setLoanStatus(loanDTO.getLoanStatus() != null ? loanDTO.getLoanStatus() : loanEntity.getLoanStatus());
                }
    
                loanRepository.persist(loanEntity);

                loanDTO.setLoanId(loanId);
                return loanDTO;
            } catch (Exception e) {
                throw new IllegalArgumentException("Something went wrong...: " + e.getMessage());
            }
        } else {
            return loanDTO;
        }
    }
}