package com.parkshare.review;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.parkshare.review.dto.CreateReviewRequest;
import com.parkshare.review.dto.ReviewResponse;
import com.parkshare.review.dto.SpotReviewsResponse;
import com.parkshare.shared.api.PagedResponse;
import com.parkshare.shared.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@WebMvcTest(ReviewController.class)
class ReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ReviewService reviewService;

    @MockBean
    private com.parkshare.auth.jwt.JwtProvider jwtProvider;

    @Test
    void createReview_withDriver_returns201() throws Exception {
        UUID driverId = UUID.randomUUID();
        CreateReviewRequest request = new CreateReviewRequest(UUID.randomUUID(), (short) 5, "Nice");
        when(reviewService.createReview(eq(driverId), any())).thenReturn(new ReviewResponse(UUID.randomUUID(), UUID.randomUUID(), driverId, 5, "Nice", null));

        mockMvc.perform(post("/api/reviews")
                        .principal(authentication(driverId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.rating").value(5));
    }

    @Test
    void getSpotReviews_isPublic() throws Exception {
        UUID spotId = UUID.randomUUID();
        when(reviewService.getSpotReviews(eq(spotId), any())).thenReturn(new SpotReviewsResponse(4.0, new PagedResponse<>(List.of(), 0, 0, 0, 20)));

        mockMvc.perform(get("/api/parking-spots/" + spotId + "/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.averageRating").value(4.0));
    }

    private static UsernamePasswordAuthenticationToken authentication(UUID userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }
}
