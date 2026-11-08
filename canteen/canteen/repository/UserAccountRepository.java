package vn.edu.phenikaa.canteen.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.edu.phenikaa.canteen.model.UserAccount;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    Optional<UserAccount> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByStudentCodeAndIdNot(String studentCode, Long id);

    boolean existsByStudentCode(String studentCode);
}
