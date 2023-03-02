package software.amazon.memorydb.user;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.collections.CollectionUtils;
import software.amazon.awssdk.services.memorydb.model.CreateUserRequest;
import software.amazon.awssdk.services.memorydb.model.DeleteUserRequest;
import software.amazon.awssdk.services.memorydb.model.DescribeUsersRequest;
import software.amazon.awssdk.services.memorydb.model.DescribeUsersResponse;
import software.amazon.awssdk.services.memorydb.model.ListTagsRequest;
import software.amazon.awssdk.services.memorydb.model.TagResourceRequest;
import software.amazon.awssdk.services.memorydb.model.UntagResourceRequest;
import software.amazon.awssdk.services.memorydb.model.UpdateUserRequest;
import software.amazon.awssdk.services.memorydb.model.User;
import software.amazon.awssdk.services.memorydb.model.Tag;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

/**
 * This class is a centralized placeholder for
 *  - api request construction
 *  - object translation to/from aws sdk
 *  - resource model construction for read/list handlers
 */

public class Translator {

  public static final int MAX_RECORDS = 50;

    /**
     * Returns true if desiredValue is not null and it is not equal to the currentValue.
     *
     * Property may be skipped from the template if no modification is needed for it, hence a property is considered as
     * modified only if value is provided and provided value is different from the current value.
     *
     * @param desiredValue requested new value
     * @param currentValue current value
     * @param <T> type of the property value
     * @return true if modification for the property is requested, otherwise false
     */
    static <T> boolean isModified(final T desiredValue, final T currentValue) {
        return (desiredValue != null && !desiredValue.equals(currentValue));
    }

    static boolean hasChangeOnCoreModelWithoutTags(final ResourceModel r1, final ResourceModel r2){
        return (Translator.isModified(r1.getAccessString(), r2.getAccessString())
            || isAuthenticationModeModified(r1.getAuthenticationMode(), r2.getAuthenticationMode()));
    }

    static boolean isAuthenticationModeModified(final AuthenticationMode a1,
        final AuthenticationMode a2) {
        boolean modified = !(a1 == null && a2 == null);
        if (a1 != null && a2 != null) {
            modified = Translator.isModified(a1.getType(), a2.getType())
                || isPasswordListModified(a1.getPasswords(), a2.getPasswords());
        }
        return modified;
    }

    static boolean isPasswordListModified(final List<String> list1, final List<String> list2) {
        boolean modified = !(CollectionUtils.isEmpty(list1) && CollectionUtils.isEmpty(list2));
        if (CollectionUtils.isNotEmpty(list1) && CollectionUtils.isNotEmpty(list2)) {
            modified = !(new HashSet<>(list1).equals(new HashSet<>(list2)));
        }
        return modified;
    }

  /**
   * Request to create a resource
   * @param model resource model
   * @return awsRequest the aws service request to create a resource
   */
  static CreateUserRequest translateToCreateRequest(final ResourceModel model,
      final ResourceHandlerRequest<ResourceModel> request) {
    return CreateUserRequest.builder()
        .userName(model.getUserName())
        .authenticationMode(
            software.amazon.awssdk.services.memorydb.model.AuthenticationMode.builder()
                .type(model.getAuthenticationMode().getType())
                .passwords(model.getAuthenticationMode().getPasswords())
              .build())
        .accessString(model.getAccessString())
        .tags(TagHelper.generateTagsForCreate(model, request))
        .build();
  }

  /**
   * Request to read a resource
   * @param model resource model
   * @return awsRequest the aws service request to describe a resource
   */
  static DescribeUsersRequest translateToReadRequest(final ResourceModel model) {
    return DescribeUsersRequest.builder()
        .userName(model.getUserName())
        .build();
  }

  /**
   * Translates resource object from sdk into a resource model
   * @param response the aws service describe resource response
   * @return model resource model
   */
  static ResourceModel translateFromReadResponse(final DescribeUsersResponse response) {
    User user = response.users().get(0);
    return ResourceModel.builder()
        .status(user.status())
        .userName(user.name())
        .accessString(user.accessString())
        .authenticationMode(
            AuthenticationMode.builder()
                .type(user.authentication().type().toString())
                .build())
        .arn(user.arn())
        .build();
  }

  /**
   * Request to delete a resource
   * @param model resource model
   * @return awsRequest the aws service request to delete a resource
   */
  static DeleteUserRequest translateToDeleteRequest(final ResourceModel model) {
    return DeleteUserRequest.builder()
        .userName(model.getUserName())
        .build();
  }

  /**
   * Request to update properties of a previously created resource
   * @param model resource model
   * @return awsRequest the aws service request to modify a resource
   */
  static UpdateUserRequest translateToUpdateRequest(final ResourceModel model) {
    return UpdateUserRequest.builder()
        .userName(model.getUserName())
        .authenticationMode(
            software.amazon.awssdk.services.memorydb.model.AuthenticationMode.builder()
                .type(model.getAuthenticationMode().getType())
                .passwords(model.getAuthenticationMode().getPasswords())
                .build())
        .accessString(model.getAccessString())
        .build();
  }

  /**
   * Request to list resources
   * @param nextToken token passed to the aws service list resources request
   * @return awsRequest the aws service request to list resources within aws account
   */
  static DescribeUsersRequest translateToListRequest(final String nextToken) {
    return DescribeUsersRequest.builder()
        .maxResults(MAX_RECORDS)
        .nextToken(nextToken)
        .build();
  }

  /**
   * Translates resource objects from sdk into a resource model (primary identifier only)
   * @param response the aws service describe resource response
   * @return list of resource models
   */
  static List<ResourceModel> translateFromListRequest(final DescribeUsersResponse response) {
    return streamOfOrEmpty(response.users())
        .map(user -> ResourceModel.builder()
            .status(user.status())
            .userName(user.name())
            .accessString(user.accessString())
            .arn(user.arn())
            .build())
        .collect(Collectors.toList());
  }

  static <T> Stream<T> streamOfOrEmpty(final Collection<T> collection) {
    return Optional.ofNullable(collection)
        .map(Collection::stream)
        .orElseGet(Stream::empty);
  }

  static Set<Tag> translateTagsToSdk(final Collection<software.amazon.memorydb.user.Tag> tags) {
    return Optional.ofNullable(tags).orElse(Collections.emptySet())
        .stream()
        .map(tag -> Tag.builder().key(tag.getKey()).value(tag.getValue()).build())
        .collect(Collectors.toSet());
  }

  static ListTagsRequest translateToListTagsRequest(final ResourceModel model) {
    return ListTagsRequest.builder().resourceArn(model.getArn()).build();
  }

  static Set<software.amazon.memorydb.user.Tag> translateTags(final Collection<Tag> tags) {
    return Optional.ofNullable(tags).orElse(Collections.emptySet())
        .stream()
        .map(tag -> software.amazon.memorydb.user.Tag.builder().key(tag.key()).value(tag.value()).build())
        .collect(Collectors.toSet());
  }

  static Set<software.amazon.memorydb.user.Tag> translateTags(final Map<String, String> tags) {
    return tags != null ? Optional.of(tags.entrySet()).orElse(Collections.emptySet())
        .stream()
        .map(entry -> software.amazon.memorydb.user.Tag.builder().key(entry.getKey()).value(entry.getValue()).build())
        .collect(Collectors.toSet()) : null;
  }

  static Map<String, String> translateTags(final Set<software.amazon.memorydb.user.Tag> tags) {
    return tags != null ? tags
        .stream()
        .collect(Collectors.toMap(software.amazon.memorydb.user.Tag::getKey, software.amazon.memorydb.user.Tag::getValue)) :
        null;
  }
  static TagResourceRequest translateToTagResourceRequest(final ResourceModel model, final List<Tag> tagsToAdd) {
    return TagResourceRequest.builder()
      .resourceArn(model.getArn())
      .tags(tagsToAdd)
      .build();
  }

  static UntagResourceRequest translateToUntagResourceRequest(final ResourceModel model, final Set<String> tagsToRemove) {
    return UntagResourceRequest.builder()
      .resourceArn(model.getArn())
      .tagKeys(tagsToRemove)
      .build();
  }

}
