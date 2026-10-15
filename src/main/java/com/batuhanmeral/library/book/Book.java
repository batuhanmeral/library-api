package com.batuhanmeral.library.book;

import com.batuhanmeral.library.author.Author;
import com.batuhanmeral.library.category.Category;
import com.batuhanmeral.library.common.audit.BaseEntity;
import com.batuhanmeral.library.common.error.BusinessRuleException;
import com.batuhanmeral.library.common.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.HashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

@Entity
@Table(name = "books")
@Getter
@Setter
@NoArgsConstructor
public class Book extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    private Long id;

    @Column(unique = true, length = 13)
    private String isbn;

    @Column(nullable = false)
    private String title;

    @Column(length = 2000)
    private String description;

    private Integer publishedYear;

    private Integer pageCount;

    /**
     * Sadece {@link #changeTotalCopies}, {@link #borrowCopy} ve {@link #returnCopy} ile değişir.
     */
    @Column(nullable = false)
    @Setter(AccessLevel.NONE)
    private int totalCopies;

    @Column(nullable = false)
    @Setter(AccessLevel.NONE)
    private int availableCopies;

    /**
     * Optimistic lock: UPDATE'e "and version = ?" eklenir; araya başka transaction girdiyse commit
     * hata verir.
     */
    @Version
    @Setter(AccessLevel.NONE)
    private Long version;

    /** Yazar ilişkisinin sahip tarafı: author_id'yi bu yazar. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private Author author;

    /**
     * Kategori ilişkisinin sahip tarafı; book_categories sadece buradan yazılır.
     * {@code @BatchSize(100)}: bir sayfanın tüm kategorileri tek sorguda yüklenir.
     */
    @ManyToMany
    @BatchSize(size = 100)
    @JoinTable(
            name = "book_categories",
            joinColumns = @JoinColumn(name = "book_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id"))
    @Setter(AccessLevel.NONE)
    private Set<Category> categories = new HashSet<>();

    /** Tüm kopyaları rafta olan yeni kitap. */
    public Book(String title, int totalCopies) {
        this.title = title;
        this.totalCopies = totalCopies;
        this.availableCopies = totalCopies;
    }

    /**
     * Toplam kopya sayısını değiştirir; ödünçteki kopyalar ödünçte kalır, toplam onların altına
     * inemez.
     */
    public void changeTotalCopies(int newTotal) {
        int onLoan = totalCopies - availableCopies;
        if (newTotal < onLoan) {
            throw new BusinessRuleException(
                    ErrorCode.COPIES_ON_LOAN,
                    "%d copies of this book are on loan; totalCopies cannot be lower."
                            .formatted(onLoan));
        }
        availableCopies = newTotal - onLoan;
        totalCopies = newTotal;
    }

    /** Ödünç için bir kopya alır. */
    public void borrowCopy() {
        if (availableCopies == 0) {
            throw new BusinessRuleException(
                    ErrorCode.BOOK_NOT_AVAILABLE, "No copy of this book is available.");
        }
        availableCopies--;
    }

    /** Ödünçten dönen kopyayı rafa koyar. */
    public void returnCopy() {
        if (availableCopies == totalCopies) {
            throw new IllegalStateException("No copy of book " + id + " is on loan.");
        }
        availableCopies++;
    }

    public void replaceCategories(Set<Category> newCategories) {
        categories.clear();
        categories.addAll(newCategories);
    }
}
