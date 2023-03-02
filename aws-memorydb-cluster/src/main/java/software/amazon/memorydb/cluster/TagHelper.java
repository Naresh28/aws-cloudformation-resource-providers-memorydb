package software.amazon.memorydb.cluster;

import com.amazonaws.util.StringUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import software.amazon.awssdk.services.memorydb.MemoryDbClient;
import software.amazon.awssdk.services.memorydb.model.DescribeClustersResponse;
import software.amazon.awssdk.services.memorydb.model.DescribeUsersResponse;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.ProxyClient;
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
  public static Map<String, String> convertToMap(final Collection<Tag> tags) {
    if (CollectionUtils.isEmpty(tags)) {
      return Collections.emptyMap();
    }
    return tags.stream()
      .filter(tag -> tag.getValue() != null)
      .collect(Collectors.toMap(
        software.amazon.memorydb.cluster.Tag::getKey,
        software.amazon.memorydb.cluster.Tag::getValue,
        (oldValue, newValue) -> newValue));
  }


  public static List<Tag> generateTagsForCreate(final ResourceModel model,
    final ResourceHandlerRequest<ResourceModel> request) {
    final List<Tag> tagList = new ArrayList<>();
    tagList.addAll(convertToList(request.getDesiredResourceTags()));
    tagList.addAll(translateTagsToSdk(model.getTags()));
    return tagList;
  }

  static Set<Tag> translateTagsToSdk(final Collection<software.amazon.memorydb.cluster.Tag> tags) {
    return Optional.ofNullable(tags).orElse(Collections.emptySet())
      .stream()
      .map(tag -> Tag.builder().key(tag.getKey()).value(tag.getValue()).build())
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
  public static List<Tag> convertToList(final Map<String, String> tagMap) {
    if (MapUtils.isEmpty(tagMap)) {
      return Collections.emptyList();
    }
    return tagMap.entrySet().stream()
      .map(tag -> Tag.builder()
        .key(tag.getKey())
        .value(tag.getValue())
        .build())
      .collect(Collectors.toList());
  }

  /**
   * generateTagsToAdd
   *
   * Determines the tags the customer desired to define or redefine.
   */
  public static Map<String, String> generateTagsToAdd(final ResourceModel resourceModel,
    final ResourceHandlerRequest<ResourceModel> request) {
    final Map<String, String> previousTags = TagHelper
      .getPreviouslyAttachedTags(request);
    final Map<String, String> desiredTags = TagHelper
      .getNewDesiredTags(resourceModel, request);
    return desiredTags.entrySet().stream()
      .filter(e -> !previousTags.containsKey(e.getKey()) || !Objects
        .equals(previousTags.get(e.getKey()), e.getValue()))
      .collect(Collectors.toMap(
        Map.Entry::getKey,
        Map.Entry::getValue));
  }

  /**
   * getTagsToRemove
   *
   * Determines the tags the customer desired to remove from the function.
   */
  public static Set<String> generateTagsToRemove(final ResourceModel resourceModel,
    final ResourceHandlerRequest<ResourceModel> request) {
    final Map<String, String> previousTags = TagHelper
      .getPreviouslyAttachedTags(request);
    final Map<String, String> desiredTags = TagHelper
      .getNewDesiredTags(resourceModel, request);
    final Set<String> desiredTagNames = desiredTags.keySet();

    return previousTags.keySet().stream()
      .filter(tagName -> !desiredTagNames.contains(tagName))
      .collect(Collectors.toSet());
  }

  /**
   * getPreviouslyAttachedTags
   *
   * If stack tags and resource tags are not merged together in Configuration class,
   * we will get previous attached user defined tags from both handlerRequest.getPreviousResourceTags (stack tags)
   * and handlerRequest.getPreviousResourceState (resource tags).
   */
  public static Map<String, String> getPreviouslyAttachedTags(final ResourceHandlerRequest<ResourceModel> handlerRequest) {
    // get previous stack level tags from handlerRequest
    final Map<String, String> previousTags = new HashMap<>();
    if (MapUtils.isNotEmpty(handlerRequest.getPreviousResourceTags())) {
      previousTags.putAll(handlerRequest.getPreviousResourceTags());
    }

    previousTags.putAll(convertToMap(handlerRequest.getPreviousResourceState().getTags()));
    return previousTags;
  }

  /**
   * getNewDesiredTags
   *
   * If stack tags and resource tags are not merged together in Configuration class,
   * we will get new user defined tags from both resource model and previous stack tags.
   */
  public static Map<String, String> getNewDesiredTags(final ResourceModel resourceModel,
    final ResourceHandlerRequest<ResourceModel> handlerRequest) {
    final Map<String, String> desiredTags = new HashMap<>();
    // get new stack level tags from handlerRequest
    if (MapUtils.isNotEmpty(handlerRequest.getDesiredResourceTags())) {
      desiredTags.putAll(handlerRequest.getDesiredResourceTags());
    }

    desiredTags.putAll(convertToMap(resourceModel.getTags()));
    return desiredTags;
  }


  public static void setModelArn(AmazonWebServicesClientProxy proxy, ProxyClient<MemoryDbClient> client,
    final ResourceModel model) {
    if (StringUtils.isNullOrEmpty(model.getARN())) {
      DescribeClustersResponse response = proxy.injectCredentialsAndInvokeV2(
        Translator.translateToReadRequest(model),
        client.client()::describeClusters);
      if (response.clusters().size() > 0) {
        model.setARN(response.clusters().get(0).arn());
      }
    }
  }

}
