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
     * Changes only through {@link #changeTotalCopies}, {@link #borrowCopy}, {@link #returnCopy}.
     */
    @Column(nullable = false)
    @Setter(AccessLevel.NONE)
    private int totalCopies;

    @Column(nullable = false)
    @Setter(AccessLevel.NONE)
    private int availableCopies;

    /**
     * Optimistic lock. Hibernate sets it to 0 on insert, increments it on every UPDATE and adds
     * "and version = ?" to the UPDATE. If another transaction changed the book after this one read
     * it, the UPDATE changes no row and the commit fails with an optimistic locking exception.
     */
    @Version
    @Setter(AccessLevel.NONE)
    private Long version;

    /** Owning side of the author relationship: it writes the author_id column. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private Author author;

    /**
     * Owning side of the book-category relationship. Category has no list of books; the
     * book_categories join table is written only from here.
     *
     * <p>When the categories of one book in a page are read, Hibernate loads those of up to 100
     * books of the same persistence context in one query ({@code where book_id in (...)}). 100 is
     * the largest page size, so a page needs one query for all its categories instead of one per
     * book.
     */
    @ManyToMany
    @BatchSize(size = 100)
    @JoinTable(
            name = "book_categories",
            joinColumns = @JoinColumn(name = "book_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id"))
    @Setter(AccessLevel.NONE)
    private Set<Category> categories = new HashSet<>();

    /** A new book with all its copies available. */
    public Book(String title, int totalCopies) {
        this.title = title;
        this.totalCopies = totalCopies;
        this.availableCopies = totalCopies;
    }

    /**
     * Changes the number of copies the library owns. Available copies move by the same amount so
     * that copies out on loan stay out on loan; the total cannot drop below them.
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

    /** Takes one copy out of the library for a loan. */
    public void borrowCopy() {
        if (availableCopies == 0) {
            throw new BusinessRuleException(
                    ErrorCode.BOOK_NOT_AVAILABLE, "No copy of this book is available.");
        }
        availableCopies--;
    }

    /** Puts a copy back after a loan. A loan can only return a copy it took. */
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
