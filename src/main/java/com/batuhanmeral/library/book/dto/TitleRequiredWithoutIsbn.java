package com.batuhanmeral.library.book.dto;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A rule about two fields of a new book: the title may only be missing when an ISBN is given. A
 * field annotation sees one value, so the rule sits on the class. The violation is reported on the
 * "title" field, so the error response looks like that of any other field rule.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = TitleRequiredWithoutIsbn.Validator.class)
public @interface TitleRequiredWithoutIsbn {

    String message() default "must not be blank when no isbn is given";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<TitleRequiredWithoutIsbn, NewBookRequest> {

        @Override
        public boolean isValid(NewBookRequest request, ConstraintValidatorContext context) {
            if (request == null || request.hasTitle() || request.isbn() != null) {
                return true;
            }
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                            context.getDefaultConstraintMessageTemplate())
                    .addPropertyNode("title")
                    .addConstraintViolation();
            return false;
        }
    }
}
