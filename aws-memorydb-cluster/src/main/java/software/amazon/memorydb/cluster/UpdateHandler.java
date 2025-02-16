package software.amazon.memorydb.cluster;

import static software.amazon.memorydb.cluster.Translator.mapToTags;
import static software.amazon.memorydb.cluster.Translator.translateTagsFromSdk;

import com.google.common.collect.Sets;
import org.apache.commons.collections.CollectionUtils;
import software.amazon.awssdk.services.memorydb.MemoryDbClient;
import software.amazon.awssdk.services.memorydb.model.Cluster;
import software.amazon.awssdk.services.memorydb.model.ClusterNotFoundException;
import software.amazon.awssdk.services.memorydb.model.MemoryDbException;
import software.amazon.awssdk.services.memorydb.model.InvalidParameterCombinationException;
import software.amazon.awssdk.services.memorydb.model.InvalidParameterValueException;
import software.amazon.awssdk.services.memorydb.model.TagResourceResponse;
import software.amazon.awssdk.services.memorydb.model.UntagResourceResponse;
import software.amazon.cloudformation.exceptions.CfnInternalFailureException;
import software.amazon.cloudformation.exceptions.CfnInvalidRequestException;
import software.amazon.cloudformation.exceptions.CfnNotFoundException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class UpdateHandler extends BaseHandlerStd {

    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(final AmazonWebServicesClientProxy proxy,
                                                                          final ResourceHandlerRequest<ResourceModel> request,
                                                                          final CallbackContext callbackContext,
                                                                          final ProxyClient<MemoryDbClient> proxyClient,
                                                                          final Logger logger) {
        logger.log(String.format("Resource model: %s", request.getDesiredResourceState()));
        return ProgressEvent.progress(request.getDesiredResourceState(), callbackContext)
                .then(progress -> updateCluster(proxy, proxyClient, progress, request, ClusterUpdateFieldType.DESCRIPTION, logger))
                .then(progress -> updateCluster(proxy, proxyClient, progress, request, ClusterUpdateFieldType.SECURITY_GROUP_IDS, logger))
                .then(progress -> updateCluster(proxy, proxyClient, progress, request, ClusterUpdateFieldType.MAINTENANCE_WINDOW, logger))
                .then(progress -> updateCluster(proxy, proxyClient, progress, request, ClusterUpdateFieldType.SNS_TOPIC_ARN, logger))
                .then(progress -> updateCluster(proxy, proxyClient, progress, request, ClusterUpdateFieldType.SNS_TOPIC_STATUS, logger))
                .then(progress -> updateCluster(proxy, proxyClient, progress, request, ClusterUpdateFieldType.SNAPSHOT_WINDOW, logger))
                .then(progress -> updateCluster(proxy, proxyClient, progress, request, ClusterUpdateFieldType.SNAPSHOT_RETENTION_LIMIT, logger))
                .then(progress -> updateCluster(proxy, proxyClient, progress, request, ClusterUpdateFieldType.NODE_TYPE, logger))
                .then(progress -> updateCluster(proxy, proxyClient, progress, request, ClusterUpdateFieldType.ENGINE_VERSION, logger))
                .then(progress -> updateCluster(proxy, proxyClient, progress, request, ClusterUpdateFieldType.PARAMETER_GROUP_NAME, logger))
                .then(progress -> updateCluster(proxy, proxyClient, progress, request, ClusterUpdateFieldType.REPLICA_CONFIGURATION, logger))
                .then(progress -> updateCluster(proxy, proxyClient, progress, request, ClusterUpdateFieldType.SHARD_CONFIGURATION, logger))
                .then(progress -> updateCluster(proxy, proxyClient, progress, request, ClusterUpdateFieldType.ACL_NAME, logger))
                .then(progress -> describeClusters(proxy, progress, proxyClient))
                .then(progress -> addTags(proxy, progress, request, proxyClient))
                .then(progress -> removeTags(proxy, progress, request, proxyClient))
                .then(progress -> listTags(proxy, progress, proxyClient))
                .then(progress -> ProgressEvent.defaultSuccessHandler(progress.getResourceModel()));
    }

    ProgressEvent<ResourceModel, CallbackContext> updateCluster(final AmazonWebServicesClientProxy proxy,
                                                                final ProxyClient<MemoryDbClient> proxyClient,
                                                                final ProgressEvent<ResourceModel, CallbackContext> progress,
                                                                final ResourceHandlerRequest<ResourceModel> request,
                                                                final ClusterUpdateFieldType fieldType,
                                                                final Logger logger) {

        final ResourceModel desiredResourceState = request.getDesiredResourceState();
        final ResourceModel currentResourceState = request.getPreviousResourceState();

        if (!isUpdateNeeded(desiredResourceState, currentResourceState, fieldType, logger)) {
            return progress;
        }

        return updateCluster(proxy, proxyClient, progress, desiredResourceState, fieldType, logger);
    }


    private boolean isUpdateNeeded(final Map<String, String> desiredResourceTags,
                                   final Map<String, String> currentResourceTags) {
        return Translator.isModified(desiredResourceTags, currentResourceTags);
    }


    private boolean isUpdateNeeded(final ResourceModel desiredResourceState,
                                   final ResourceModel currentResourceState,
                                   final ClusterUpdateFieldType fieldType,
                                   final Logger logger) {
        boolean isModified;
        switch (fieldType) {
            case DESCRIPTION:
                isModified = Translator.isModified(desiredResourceState.getDescription(), currentResourceState.getDescription());
                break;
            case SECURITY_GROUP_IDS:
                isModified = Translator.isModified(desiredResourceState.getSecurityGroupIds(), currentResourceState.getSecurityGroupIds());
                break;
            case MAINTENANCE_WINDOW:
                isModified = Translator.isModified(desiredResourceState.getMaintenanceWindow(), currentResourceState.getMaintenanceWindow());
                break;
            case SNS_TOPIC_ARN:
                isModified = Translator.isModified(desiredResourceState.getSnsTopicArn(), currentResourceState.getSnsTopicArn());
                break;
            case SNS_TOPIC_STATUS:
                isModified = Translator.isModified(desiredResourceState.getSnsTopicStatus(), currentResourceState.getSnsTopicStatus());
                break;
            case PARAMETER_GROUP_NAME:
                isModified = Translator.isModified(desiredResourceState.getParameterGroupName(), currentResourceState.getParameterGroupName());
                break;
            case SNAPSHOT_WINDOW:
                isModified = Translator.isModified(desiredResourceState.getSnapshotWindow(), currentResourceState.getSnapshotWindow());
                break;
            case SNAPSHOT_RETENTION_LIMIT:
                isModified = Translator.isModified(desiredResourceState.getSnapshotRetentionLimit(), currentResourceState.getSnapshotRetentionLimit());
                break;
            case NODE_TYPE:
                isModified = Translator.isModified(desiredResourceState.getNodeType(), currentResourceState.getNodeType());
                break;
            case ENGINE_VERSION:
                isModified = Translator.isModified(desiredResourceState.getEngineVersion(), currentResourceState.getEngineVersion());
                break;
            case REPLICA_CONFIGURATION:
                isModified = Translator.isModified(desiredResourceState.getNumReplicasPerShard(), currentResourceState.getNumReplicasPerShard());
                break;
            case SHARD_CONFIGURATION:
                isModified = Translator.isModified(desiredResourceState.getNumShards(), currentResourceState.getNumShards());
                break;
            case ACL_NAME:
                isModified = Translator.isModified(desiredResourceState.getACLName(), currentResourceState.getACLName());
                break;
            default:
                logger.log(String.format("Modification type [%s] not supported", fieldType));
                throw new CfnInternalFailureException();
        }
        return isModified;
    }

    ProgressEvent<ResourceModel, CallbackContext> updateCluster(final AmazonWebServicesClientProxy proxy,
                                                                final ProxyClient<MemoryDbClient> proxyClient,
                                                                final ProgressEvent<ResourceModel, CallbackContext> progress,
                                                                final ResourceModel desiredResourceState,
                                                                final ClusterUpdateFieldType fieldType,
                                                                final Logger logger) {
        logger.log(String.format("Updating fieldType : %s" , fieldType.name()));
        return proxy.initiate("AWS-memorydb-Cluster::Update", proxyClient, progress.getResourceModel(), progress.getCallbackContext())
                .translateToServiceRequest(model -> Translator.translateToUpdateRequest(model, fieldType))
                .backoffDelay(STABILIZATION_DELAY)
                .makeServiceCall((awsRequest, memoryDbClientProxyClient) -> handleExceptions(() ->  memoryDbClientProxyClient.injectCredentialsAndInvokeV2(awsRequest, memoryDbClientProxyClient.client()::updateCluster)))
                .stabilize((awsRequest, awsResponse, client, model, context) -> {
                    try {
                        final Cluster cluster = getCluster(proxy, client, model);
                        boolean isStabilized = STABILIZED_STATUS.contains(cluster.status());
                        if (isStabilized == false) {
                            return false;
                        }
                        final ResourceModel postUpdateResourceState = Translator.translateFromReadResponse(cluster);
                        if (isUpdateNeeded(desiredResourceState, postUpdateResourceState, fieldType, logger)) {
                            /* Resource has been stabilized, however update operation has not been completed.
                             * This is possible, since an update operation can fail to service failures (Example: requested
                             * node type is not currently available).
                             */
                            throw MemoryDbException.builder().message(UPDATE_FAILED_WITH_STABILIZATION_SUCCESS).build();
                        }
                        return true;
                    } catch (final ClusterNotFoundException e) {
                        throw new CfnNotFoundException(e);
                    } catch (final InvalidParameterValueException | InvalidParameterCombinationException e) {
                        throw new CfnInvalidRequestException(e);
                    } catch (final Exception e) {
                        throw e;
                    }
                })
                .progress();
    }

    public ProgressEvent<ResourceModel, CallbackContext> addTags(
        AmazonWebServicesClientProxy proxy,
        ProgressEvent<ResourceModel, CallbackContext> progress,
        ResourceHandlerRequest<ResourceModel> request,
        ProxyClient<MemoryDbClient> proxyClient
    ) {
        final Map<String, String> tagsToAdd = TagHelper
            .generateTagsToAdd(progress.getResourceModel(), request);
        if (!tagsToAdd.isEmpty()) {
            // If ARN is null, then do a describeCall and set ARN in model. This is needed since we have a contract test
            // with null ARN.
            TagHelper.setModelArn(proxy, proxyClient, progress.getResourceModel());
            return proxy
                .initiate("AWS-MemoryDB-Cluster::AddTags", proxyClient, progress.getResourceModel(),
                    progress.getCallbackContext())
                .translateToServiceRequest((model) -> Translator
                    .translateToTagResourceRequest(progress.getResourceModel().getARN(),
                        TagHelper.convertToList(tagsToAdd)))
                .makeServiceCall((awsRequest, client) -> handleExceptions(() ->
                    client.injectCredentialsAndInvokeV2(awsRequest,
                        client.client()::tagResource)))
                .stabilize(
                    (addTagsRequest, addTagsResponse, proxyInvocation, model, context) -> isStabilized(
                        proxy, proxyInvocation, model))
                .progress();
        }
        return progress;
    }

    public ProgressEvent<ResourceModel, CallbackContext> removeTags(
        AmazonWebServicesClientProxy proxy,
        ProgressEvent<ResourceModel, CallbackContext> progress,
        ResourceHandlerRequest<ResourceModel> request,
        ProxyClient<MemoryDbClient> proxyClient
    ) {
        final Set<String> tagsToRemove = TagHelper
            .generateTagsToRemove(progress.getResourceModel(), request);
        if (!tagsToRemove.isEmpty()) {
            // If ARN is null, then do a describeCall and set ARN in model. This is needed since we have a contract test
            // with null ARN.
            TagHelper.setModelArn(proxy, proxyClient, progress.getResourceModel());
            return proxy
                .initiate("AWS-MemoryDB-User::RemoveTags", proxyClient, progress.getResourceModel(),
                    progress.getCallbackContext())
                .translateToServiceRequest((model) -> Translator
                    .translateToUntagResourceRequest(progress.getResourceModel().getARN(), tagsToRemove))
                .makeServiceCall((awsRequest, client) -> handleExceptions(() ->
                    client.injectCredentialsAndInvokeV2(awsRequest,
                        client.client()::untagResource)))
                .stabilize(
                    (addTagsRequest, addTagsResponse, proxyInvocation, model, context) -> isStabilized(
                        proxy, proxyInvocation, model))
                .progress();
        }
        return progress;
    }
}
