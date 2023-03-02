package software.amazon.memorydb.acl;


import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

public class TagHelper {

  /**
   * convertToMap
   *
   * Converts a collection of Tag objects to a tag-name -> tag-value map.
   *
   * Note: Tag objects with null tag values will not be included in the output
   * map.
   *
   * @param tags Collection of tags to convert
   * @return Converted Map of tags
   */
  public static Map<String, String> convertToMap(final Collection<software.amazon.memorydb.acl.Tag> tags) {
    if (CollectionUtils.isEmpty(tags)) {
      return Collections.emptyMap();
    }
    return tags.stream()
      .filter(tag -> tag.getValue() != null)
      .collect(Collectors.toMap(
          software.amazon.memorydb.acl.Tag::getKey,
          software.amazon.memorydb.acl.Tag::getValue,
        (oldValue, newValue) -> newValue));
  }


  public static List<software.amazon.awssdk.services.memorydb.model.Tag> generateTagsForCreate(final ResourceModel model,
    final ResourceHandlerRequest<ResourceModel> request) {
    final List<software.amazon.awssdk.services.memorydb.model.Tag> tagList = new ArrayList<>();
    tagList.addAll(convertToList(request.getDesiredResourceTags()));
    tagList.addAll(translateTagsToSdk(model.getTags()));
    return tagList;
  }

  static Set<software.amazon.awssdk.services.memorydb.model.Tag> translateTagsToSdk(final Collection<software.amazon.memorydb.acl.Tag> tags) {
    return Optional.ofNullable(tags).orElse(Collections.emptySet())
      .stream()
      .map(tag -> software.amazon.awssdk.services.memorydb.model.Tag.builder().key(tag.getKey()).value(tag.getValue()).build())
      .collect(Collectors.toSet());
  }

  /**
   * convertToSet
   *
   * Converts a tag map to a set of Tag objects.
   *
   * Note: Like convertToMap, convertToSet filters out value-less tag entries.
   *
   * @param tagMap Map of tags to convert
   * @return Set of Tag objects
   */
  public static List<software.amazon.awssdk.services.memorydb.model.Tag> convertToList(
      final Map<String, String> tagMap) {
    if (MapUtils.isEmpty(tagMap)) {
      return Collections.emptyList();
    }
    return tagMap.entrySet().stream()
      .map(tag -> software.amazon.awssdk.services.memorydb.model.Tag.builder()
        .key(tag.getKey())
        .value(tag.getValue())
        .build())
      .collect(Collectors.toList());
  }

}
