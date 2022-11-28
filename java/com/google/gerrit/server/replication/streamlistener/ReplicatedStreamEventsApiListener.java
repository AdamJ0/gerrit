package com.google.gerrit.server.replication.streamlistener;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.events.AgreementSignupListener;
import com.google.gerrit.extensions.events.AssigneeChangedListener;
import com.google.gerrit.extensions.events.ChangeAbandonedListener;
import com.google.gerrit.extensions.events.ChangeDeletedListener;
import com.google.gerrit.extensions.events.ChangeMergedListener;
import com.google.gerrit.extensions.events.ChangeRestoredListener;
import com.google.gerrit.extensions.events.ChangeRevertedListener;
import com.google.gerrit.extensions.events.CommentAddedListener;
import com.google.gerrit.extensions.events.GarbageCollectorListener;
import com.google.gerrit.extensions.events.GitReferenceUpdatedListener;
import com.google.gerrit.extensions.events.HashtagsEditedListener;
import com.google.gerrit.extensions.events.NewProjectCreatedListener;
import com.google.gerrit.extensions.events.PrivateStateChangedListener;
import com.google.gerrit.extensions.events.ReplicatedStreamEvent;
import com.google.gerrit.extensions.events.ReplicatedStreamEventListener;
import com.google.gerrit.extensions.events.ReviewerAddedListener;
import com.google.gerrit.extensions.events.ReviewerDeletedListener;
import com.google.gerrit.extensions.events.RevisionCreatedListener;
import com.google.gerrit.extensions.events.TopicEditedListener;
import com.google.gerrit.extensions.events.VoteDeletedListener;
import com.google.gerrit.extensions.events.WorkInProgressStateChangedListener;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.server.events.EventBroker;
import com.google.gerrit.server.extensions.events.AgreementSignup;
import com.google.gerrit.server.extensions.events.AssigneeChanged;
import com.google.gerrit.server.extensions.events.ChangeAbandoned;
import com.google.gerrit.server.extensions.events.ChangeDeleted;
import com.google.gerrit.server.extensions.events.ChangeMerged;
import com.google.gerrit.server.extensions.events.ChangeRestored;
import com.google.gerrit.server.extensions.events.ChangeReverted;
import com.google.gerrit.server.extensions.events.CommentAdded;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.extensions.events.HashtagsEdited;
import com.google.gerrit.server.extensions.events.PrivateStateChanged;
import com.google.gerrit.server.extensions.events.ReviewerAdded;
import com.google.gerrit.server.extensions.events.ReviewerDeleted;
import com.google.gerrit.server.extensions.events.RevisionCreated;
import com.google.gerrit.server.extensions.events.TopicEdited;
import com.google.gerrit.server.extensions.events.VoteDeleted;
import com.google.gerrit.server.extensions.events.WorkInProgressStateChanged;
import com.google.gerrit.server.git.GarbageCollection;
import com.google.gerrit.server.replication.coordinators.ReplicatedEventsCoordinator;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;

public class ReplicatedStreamEventsApiListener implements ReplicatedStreamEventListener,
        AgreementSignupListener,
        AssigneeChangedListener,
        ChangeAbandonedListener,
        ChangeDeletedListener,
        ChangeMergedListener,
        ChangeRestoredListener,
        ChangeRevertedListener,
        WorkInProgressStateChangedListener,
        PrivateStateChangedListener,
        CommentAddedListener,
        GitReferenceUpdatedListener,
        HashtagsEditedListener,
        ReviewerAddedListener,
        ReviewerDeletedListener,
        RevisionCreatedListener,
        TopicEditedListener,
        VoteDeletedListener,
        GarbageCollectorListener{

    private static final FluentLogger logger = FluentLogger.forEnclosingClass();

    private final EventBroker eventBroker;
    private final String localNodeId;
    private final CommentAdded commentAdded;
    private final AgreementSignup agreementSignup;
    private final AssigneeChanged assigneeChanged;
    private final ChangeAbandoned changeAbandoned;
    private final ChangeReverted changeReverted;
    private final ChangeDeleted changeDeleted;
    private final ChangeMerged changeMerged;
    private final ChangeRestored changeRestored;
    private final GitReferenceUpdated gitReferenceUpdated;
    private final HashtagsEdited hashtagsEdited;
    private final PrivateStateChanged privateStateChanged;
    private final ReviewerAdded reviewerAdded;
    private final ReviewerDeleted reviewerDeleted;
    private final RevisionCreated revisionCreated;
    private final TopicEdited topicEdited;
    private final VoteDeleted voteDeleted;
    private final WorkInProgressStateChanged workInProgressStateChanged;
    private final GarbageCollection garbageCollection;

    private final ReplicatedEventsCoordinator replicatedEventsCoordinator;



    public static class Module extends AbstractModule {
        @Override
        protected void configure() {
            DynamicItem.itemOf(binder(), ReplicatedStreamEventsApiListener.class);
            DynamicItem.bind(binder(), ReplicatedStreamEventListener.class).to(ReplicatedStreamEventsApiListener.class);
            DynamicSet.bind(binder(), AgreementSignupListener.class).to(ReplicatedStreamEventsApiListener.class);
            DynamicSet.bind(binder(), AssigneeChangedListener.class).to(ReplicatedStreamEventsApiListener.class);
            DynamicSet.bind(binder(), ChangeAbandonedListener.class).to(ReplicatedStreamEventsApiListener.class);
            DynamicSet.bind(binder(), ChangeDeletedListener.class).to(ReplicatedStreamEventsApiListener.class);
            DynamicSet.bind(binder(), ChangeMergedListener.class).to(ReplicatedStreamEventsApiListener.class);
            DynamicSet.bind(binder(), ChangeRestoredListener.class).to(ReplicatedStreamEventsApiListener.class);
            DynamicSet.bind(binder(), ChangeRevertedListener.class).to(ReplicatedStreamEventsApiListener.class);
            DynamicSet.bind(binder(), CommentAddedListener.class).to(ReplicatedStreamEventsApiListener.class);
            DynamicSet.bind(binder(), GitReferenceUpdatedListener.class).to(ReplicatedStreamEventsApiListener.class);
            DynamicSet.bind(binder(), HashtagsEditedListener.class).to(ReplicatedStreamEventsApiListener.class);
            DynamicSet.bind(binder(), PrivateStateChangedListener.class).to(ReplicatedStreamEventsApiListener.class);
            DynamicSet.bind(binder(), ReviewerAddedListener.class).to(ReplicatedStreamEventsApiListener.class);
            DynamicSet.bind(binder(), ReviewerDeletedListener.class).to(ReplicatedStreamEventsApiListener.class);
            DynamicSet.bind(binder(), RevisionCreatedListener.class).to(ReplicatedStreamEventsApiListener.class);
            DynamicSet.bind(binder(), TopicEditedListener.class).to(ReplicatedStreamEventsApiListener.class);
            DynamicSet.bind(binder(), VoteDeletedListener.class).to(ReplicatedStreamEventsApiListener.class);
            DynamicSet.bind(binder(), WorkInProgressStateChangedListener.class).to(ReplicatedStreamEventsApiListener.class);
            DynamicSet.bind(binder(), GarbageCollectorListener.class).to(ReplicatedStreamEventsApiListener.class);

        }
    }


    @Inject
    ReplicatedStreamEventsApiListener(EventBroker eventBroker,
                                         AgreementSignup agreementSignup,
                                         AssigneeChanged assigneeChanged,
                                         ChangeAbandoned changeAbandoned,
                                         ChangeDeleted changeDeleted,
                                         ChangeMerged changeMerged,
                                         ChangeRestored changeRestored,
                                         ChangeReverted changeReverted,
                                         CommentAdded commentAdded,
                                         GitReferenceUpdated gitReferenceUpdated,
                                         HashtagsEdited hashtagsEdited,
                                         PrivateStateChanged privateStateChanged,
                                         ReviewerAdded reviewerAdded,
                                         ReviewerDeleted reviewerDeleted,
                                         RevisionCreated revisionCreated,
                                         TopicEdited topicEdited,
                                         VoteDeleted voteDeleted,
                                         WorkInProgressStateChanged workInProgressStateChanged,
                                         GarbageCollection garbageCollection,
                                         ReplicatedEventsCoordinator replicatedEventsCoordinator){
        this.eventBroker = eventBroker;
        this.agreementSignup = agreementSignup;
        this.assigneeChanged = assigneeChanged;
        this.changeAbandoned = changeAbandoned;
        this.changeReverted = changeReverted;
        this.changeDeleted = changeDeleted;
        this.changeMerged = changeMerged;
        this.changeRestored = changeRestored;
        this.commentAdded = commentAdded;
        this.gitReferenceUpdated = gitReferenceUpdated;
        this.hashtagsEdited = hashtagsEdited;
        this.privateStateChanged = privateStateChanged;
        this.reviewerAdded = reviewerAdded;
        this.reviewerDeleted = reviewerDeleted;
        this.revisionCreated = revisionCreated;
        this.topicEdited = topicEdited;
        this.voteDeleted = voteDeleted;
        this.workInProgressStateChanged = workInProgressStateChanged;
        this.garbageCollection = garbageCollection;
        this.replicatedEventsCoordinator = replicatedEventsCoordinator;
        this.localNodeId = replicatedEventsCoordinator.getThisNodeIdentity();
    }


    private void postEventIfLocal(ReplicatedStreamEvent event) {
        String eventNodeId  = event.nodeIdentity();
        // Only post event here if we are on the originating node. We do this so we can offer the event to the extension
        // events queue in the ReplicatedEventsWorker. We must not call this on a remote site as we would end up in a
        // cycle of postEvent() -> onReplicatedExtensionEvent() -> fire() -> postEvent()
        if(localNodeId.equals(eventNodeId)) {
            logger.atFine().log("About to post event to local event stream %s", event.toString());
            eventBroker.postEvent(event);
        }
    }

    @Override
    public void onAgreementSignup(AgreementSignupListener.Event event) { postEventIfLocal(event);}

    @Override
    public void onAssigneeChanged(AssigneeChangedListener.Event event) { postEventIfLocal(event); }

    @Override
    public void onChangeAbandoned(ChangeAbandonedListener.Event event) { postEventIfLocal(event); }

    @Override
    public void onChangeDeleted(ChangeDeletedListener.Event event) { postEventIfLocal(event); }

    @Override
    public void onChangeMerged(ChangeMergedListener.Event event) { postEventIfLocal(event); }

    @Override
    public void onChangeRestored(ChangeRestoredListener.Event event) { postEventIfLocal(event); }

    @Override
    public void onChangeReverted(ChangeRevertedListener.Event event) { postEventIfLocal(event); }

    @Override
    public void onCommentAdded(CommentAddedListener.Event event) { postEventIfLocal(event); }

    @Override
    public void onGitReferenceUpdated(GitReferenceUpdatedListener.Event event) { postEventIfLocal(event); }

    @Override
    public void onHashtagsEdited(HashtagsEditedListener.Event event) { postEventIfLocal(event); }

    @Override
    public void onPrivateStateChanged(PrivateStateChangedListener.Event event) { postEventIfLocal(event); }

    @Override
    public void onReviewersAdded(ReviewerAddedListener.Event event) { postEventIfLocal(event); }

    @Override
    public void onReviewerDeleted(ReviewerDeletedListener.Event event) { postEventIfLocal(event); }

    @Override
    public void onRevisionCreated(RevisionCreatedListener.Event event) { postEventIfLocal(event); }

    @Override
    public void onTopicEdited(TopicEditedListener.Event event) { postEventIfLocal(event); }

    @Override
    public void onVoteDeleted(VoteDeletedListener.Event event) { postEventIfLocal(event); }

    @Override
    public void onWorkInProgressStateChanged(WorkInProgressStateChangedListener.Event event) { postEventIfLocal(event); }

    @Override
    public void onGarbageCollected(GarbageCollectorListener.Event event) { postEventIfLocal(event); }


    /**
     * If we are the originating node that fired the extension event then the EventBroker will post that event as a
     * ReplicatedStreamEvent, which in turn will be heard the by ReplicatedStreamEventApiListener.
     *
     * * If we are the originating node then onReplicatedStreamEvent will not do anything and just return.
     *   The ReplicatedStreamEventApiListener registered against the OutgoingStreamEventsFeedListener
     *   thread will be used to offer the event to the replicated stream event queue in ReplicatedOutgoingStreamEventsFeed
     *   so that the event can be replicated to all sites via the event file mechanism.
     *
     * * If we are on a remote site (i.e. not the originating node), then onReplicatedStreamEvent will
     *   call the appropriate overridden implementation of the fire() method for its underlying stream event type.
     * @param event : is a ReplicatedStreamEvent received as a result of the EventBroker posting the event
     *              for the registered ReplicatedStreamEventsApiListener
     */
    @Override
    public void onReplicatedStreamEvent(ReplicatedStreamEvent event) {

        // We only want to continue here if the event has already been replicated and we are on a remote site.
        // If we are on the originator node then we do not call this overloaded version of the fire() method.
        if(!event.replicationSuccessful() || localNodeId.equals(event.nodeIdentity())){
            return;
        }

        if(event instanceof AgreementSignupListener.Event){
            agreementSignup.fire((AgreementSignupListener.Event) event);
        } else if( event instanceof AssigneeChangedListener.Event){
            assigneeChanged.fire((AssigneeChangedListener.Event) event);
        } else if(event instanceof ChangeAbandonedListener.Event){
            changeAbandoned.fire((ChangeAbandonedListener.Event) event);
        } else if(event instanceof ChangeDeletedListener.Event) {
            changeDeleted.fire((ChangeDeletedListener.Event) event);
        }else if(event instanceof ChangeRevertedListener.Event){
            changeReverted.fire((ChangeRevertedListener.Event) event);
        } else if(event instanceof ChangeMergedListener.Event){
            changeMerged.fire((ChangeMergedListener.Event) event);
        } else if(event instanceof ChangeRestoredListener.Event){
            changeRestored.fire((ChangeRestoredListener.Event) event);
        } else if(event instanceof CommentAddedListener.Event){
            commentAdded.fire((CommentAddedListener.Event) event);
        } else if( event instanceof GitReferenceUpdatedListener.Event){
            gitReferenceUpdated.fire((GitReferenceUpdatedListener.Event) event);
        } else if (event instanceof HashtagsEditedListener.Event){
            hashtagsEdited.fire((HashtagsEditedListener.Event) event);
        } else if(event instanceof PrivateStateChangedListener.Event){
            privateStateChanged.fire((PrivateStateChangedListener.Event) event);
        } else if(event instanceof ReviewerAddedListener.Event){
            reviewerAdded.fire((ReviewerAddedListener.Event) event);
        } else if(event instanceof ReviewerDeletedListener.Event){
            reviewerDeleted.fire((ReviewerDeletedListener.Event) event);
        } else if(event instanceof RevisionCreatedListener.Event){
            revisionCreated.fire((RevisionCreatedListener.Event) event);
        } else if(event instanceof TopicEditedListener.Event){
            topicEdited.fire((TopicEditedListener.Event) event);
        } else if(event instanceof VoteDeletedListener.Event){
            voteDeleted.fire((VoteDeletedListener.Event) event);
        } else if(event instanceof WorkInProgressStateChangedListener.Event) {
            workInProgressStateChanged.fire((WorkInProgressStateChangedListener.Event) event);
        } else if(event instanceof GarbageCollectorListener.Event) {
            garbageCollection.fire((GarbageCollectorListener.Event) event);
        }
    }

}
