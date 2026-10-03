package com.batuhanmeral.library.book;

import com.batuhanmeral.library.author.Author;
import com.batuhanmeral.library.category.Category;
import com.batuhanmeral.library.common.audit.BaseEntity;
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
import java.util.HashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

    @Column(nullable = false)
    private int totalCopies;

    @Column(nullable = false)
    private int availableCopies;

    /** Owning side of the author relationship: it writes the author_id column. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private Author author;

    /**
     * Owning side of the book-category relationship. Category has no list of books; the
     * book_categories join table is written only from here.
     */
    @ManyToMany
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
     * that copies out on loan stay out on loan.
     */
    public void changeTotalCopies(int newTotal) {
        availableCopies += newTotal - totalCopies;
        totalCopies = newTotal;
    }

    public void replaceCategories(Set<Category> newCategories) {
        categories.clear();
        categories.addAll(newCategories);
    }
}
