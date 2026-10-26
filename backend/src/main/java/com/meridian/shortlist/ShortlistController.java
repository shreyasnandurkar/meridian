package com.meridian.shortlist;

import com.meridian.common.CurrentUser;
import com.meridian.shortlist.ShortlistDtos.AddItemRequest;
import com.meridian.shortlist.ShortlistDtos.ItemView;
import com.meridian.shortlist.ShortlistDtos.LinkPreviewView;
import com.meridian.shortlist.ShortlistDtos.ReactionRequest;
import com.meridian.shortlist.ShortlistDtos.UpdateItemRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/trips/{tripId}")
public class ShortlistController {

    private final ShortlistService shortlist;

    public ShortlistController(ShortlistService shortlist) {
        this.shortlist = shortlist;
    }

    @GetMapping("/shortlist")
    public List<ItemView> list(@PathVariable UUID tripId, @AuthenticationPrincipal Jwt jwt) {
        return shortlist.list(tripId, CurrentUser.id(jwt));
    }

    @PostMapping("/shortlist")
    @ResponseStatus(HttpStatus.CREATED)
    public ItemView add(@PathVariable UUID tripId, @Valid @RequestBody AddItemRequest req,
            @AuthenticationPrincipal Jwt jwt) {
        return shortlist.add(tripId, CurrentUser.id(jwt), req);
    }

    @PatchMapping("/shortlist/{itemId}")
    public ItemView update(@PathVariable UUID tripId, @PathVariable UUID itemId,
            @Valid @RequestBody UpdateItemRequest req, @AuthenticationPrincipal Jwt jwt) {
        return shortlist.update(tripId, itemId, CurrentUser.id(jwt), req);
    }

    @DeleteMapping("/shortlist/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable UUID tripId, @PathVariable UUID itemId, @AuthenticationPrincipal Jwt jwt) {
        shortlist.remove(tripId, itemId, CurrentUser.id(jwt));
    }

    @PutMapping("/shortlist/{itemId}/reaction")
    public ItemView react(@PathVariable UUID tripId, @PathVariable UUID itemId,
            @Valid @RequestBody ReactionRequest req, @AuthenticationPrincipal Jwt jwt) {
        return shortlist.react(tripId, itemId, CurrentUser.id(jwt), req);
    }

    @DeleteMapping("/shortlist/{itemId}/reaction")
    public ItemView clearReaction(@PathVariable UUID tripId, @PathVariable UUID itemId,
            @AuthenticationPrincipal Jwt jwt) {
        return shortlist.clearReaction(tripId, itemId, CurrentUser.id(jwt));
    }

    /** Title of a pasted link, read server-side behind the SSRF guard. */
    @GetMapping("/link-preview")
    public LinkPreviewView linkPreview(@PathVariable UUID tripId, @RequestParam @Size(max = 2048) String url,
            @AuthenticationPrincipal Jwt jwt) {
        return shortlist.preview(tripId, CurrentUser.id(jwt), url);
    }
}
