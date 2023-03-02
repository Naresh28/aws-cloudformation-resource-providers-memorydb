package software.amazon.memorydb.user;

import java.util.Map;
import java.util.Set;
import software.amazon.awssdk.services.memorydb.MemoryDbClient;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ProxyClient;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;

public class UpdateHandler extends BaseHandlerStd {

    private Logger logger;

    @Override
    protected ProgressEvent<ResourceModel, CallbackContext> handleRequest(
        final AmazonWebServicesClientProxy proxy,
        final ResourceHandlerRequest<ResourceModel> request,
        final CallbackContext callbackContext,
        final ProxyClient<MemoryDbClient> proxyClient,
        final Logger logger) {

        this.logger = logger;

        return ProgressEvent.progress(request.getDesiredResourceState(), callbackContext)
            .then(progress -> updateUser(proxy, progress, request, proxyClient))
            .then(progress -> addTags(proxy, progress, request, proxyClient))
            .then(progress -> removeTags(proxy, progress, request, proxyClient))
            .then(progress -> new ReadHandler()
                .handleRequest(proxy, request, callbackContext, proxyClient, logger));
    }

    private ProgressEvent<ResourceModel, CallbackContext> updateUser(
        AmazonWebServicesClientProxy proxy,
        ProgressEvent<ResourceModel, CallbackContext> progress,
        ResourceHandlerRequest<ResourceModel> request,
        ProxyClient<MemoryDbClient> proxyClient
    ) {
        if (Translator.hasChangeOnCoreModelWithoutTags(request.getDesiredResourceState(),
            request.getPreviousResourceState())) {
            return proxy
                .initiate("AWS-MemoryDB-User::Update", proxyClient, progress.getResourceModel(),
                    progress.getCallbackContext())
                .translateToServiceRequest(Translator::translateToUpdateRequest)
                .makeServiceCall((awsRequest, client) -> handleExceptions(() ->
                    client.injectCredentialsAndInvokeV2(awsRequest, client.client()::updateUser)))
                .stabilize(
                    (updateUserRequest, updateUserResponse, proxyInvocation, model, context) -> isUserStabilized(
                        proxyInvocation, model, logger))
                .progress();
        } else {
            return progress;
        }

    }

    private ProgressEvent<ResourceModel, CallbackContext> addTags(
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
                .initiate("AWS-MemoryDB-User::AddTags", proxyClient, progress.getResourceModel(),
                    progress.getCallbackContext())
                .translateToServiceRequest((model) -> Translator
                    .translateToTagResourceRequest(progress.getResourceModel(),
                        TagHelper.convertToList(tagsToAdd)))
                .makeServiceCall((awsRequest, client) -> handleExceptions(() ->
                    client.injectCredentialsAndInvokeV2(awsRequest,
                        client.client()::tagResource)))
                .stabilize(
                    (addTagsRequest, addTagsResponse, proxyInvocation, model, context) -> isUserStabilized(
                        proxyInvocation, model, logger))
                .progress();
        }
        return progress;
    }

    private ProgressEvent<ResourceModel, CallbackContext> removeTags(
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
                    .translateToUntagResourceRequest(progress.getResourceModel(), tagsToRemove))
                .makeServiceCall((awsRequest, client) -> handleExceptions(() ->
                    client.injectCredentialsAndInvokeV2(awsRequest,
                        client.client()::untagResource)))
                .stabilize(
                    (addTagsRequest, addTagsResponse, proxyInvocation, model, context) -> isUserStabilized(
                        proxyInvocation, model, logger))
                .progress();
        }
        return progress;
    }
}
