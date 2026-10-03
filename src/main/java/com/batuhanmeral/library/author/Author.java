package com.batuhanmeral.library.author;

import com.batuhanmeral.library.book.Book;
import com.batuhanmeral.library.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "authors")
@Getter
@Setter
@NoArgsConstructor
public class Author extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 4000)
    private String biography;

    private LocalDate birthDate;

    /**
     * Inverse side of {@link Book#getAuthor()}. Hibernate reads it but never writes it: the
     * author_id column changes only through Book.author. Lazy by default for collections.
     */
    @OneToMany(mappedBy = "author")
    @Setter(AccessLevel.NONE)
    private List<Book> books = new ArrayList<>();

    public Author(String name) {
        this.name = name;
    }
}
