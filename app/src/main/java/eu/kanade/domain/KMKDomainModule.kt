package eu.kanade.domain

import tachiyomi.data.authorSubscription.AuthorSubscriptionRepositoryImpl
import tachiyomi.data.libraryUpdateError.LibraryUpdateErrorRepositoryImpl
import tachiyomi.data.libraryUpdateError.LibraryUpdateErrorWithRelationsRepositoryImpl
import tachiyomi.data.libraryUpdateErrorMessage.LibraryUpdateErrorMessageRepositoryImpl
import tachiyomi.domain.authorSubscription.interactor.DeleteAuthorSubscription
import tachiyomi.domain.authorSubscription.interactor.GetAuthorSubscriptions
import tachiyomi.domain.authorSubscription.interactor.MoveAuthorSubscriptionToBottom
import tachiyomi.domain.authorSubscription.interactor.MoveAuthorSubscriptionToTop
import tachiyomi.domain.authorSubscription.interactor.ReorderAuthorSubscriptions
import tachiyomi.domain.authorSubscription.interactor.ToggleAuthorSubscriptionPinned
import tachiyomi.domain.authorSubscription.interactor.UpdateAuthorSubscriptionRefreshTime
import tachiyomi.domain.authorSubscription.interactor.UpsertAuthorSubscription
import tachiyomi.domain.authorSubscription.repository.AuthorSubscriptionRepository
import tachiyomi.domain.libraryUpdateError.interactor.DeleteLibraryUpdateErrors
import tachiyomi.domain.libraryUpdateError.interactor.GetLibraryUpdateErrorWithRelations
import tachiyomi.domain.libraryUpdateError.interactor.GetLibraryUpdateErrors
import tachiyomi.domain.libraryUpdateError.interactor.InsertLibraryUpdateErrors
import tachiyomi.domain.libraryUpdateError.repository.LibraryUpdateErrorRepository
import tachiyomi.domain.libraryUpdateError.repository.LibraryUpdateErrorWithRelationsRepository
import tachiyomi.domain.libraryUpdateErrorMessage.interactor.DeleteLibraryUpdateErrorMessages
import tachiyomi.domain.libraryUpdateErrorMessage.interactor.GetLibraryUpdateErrorMessages
import tachiyomi.domain.libraryUpdateErrorMessage.interactor.InsertLibraryUpdateErrorMessages
import tachiyomi.domain.libraryUpdateErrorMessage.repository.LibraryUpdateErrorMessageRepository
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addFactory
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get

class KMKDomainModule : InjektModule {

    override fun InjektRegistrar.registerInjectables() {
        addSingletonFactory<AuthorSubscriptionRepository> { AuthorSubscriptionRepositoryImpl(get()) }
        addFactory { GetAuthorSubscriptions(get()) }
        addFactory { UpsertAuthorSubscription(get()) }
        addFactory { DeleteAuthorSubscription(get()) }
        addFactory { UpdateAuthorSubscriptionRefreshTime(get()) }
        addFactory { ReorderAuthorSubscriptions(get()) }
        addFactory { MoveAuthorSubscriptionToTop(get()) }
        addFactory { MoveAuthorSubscriptionToBottom(get()) }
        addFactory { ToggleAuthorSubscriptionPinned(get()) }

        addSingletonFactory<LibraryUpdateErrorWithRelationsRepository> {
            LibraryUpdateErrorWithRelationsRepositoryImpl(get())
        }
        addFactory { GetLibraryUpdateErrorWithRelations(get()) }

        addSingletonFactory<LibraryUpdateErrorMessageRepository> { LibraryUpdateErrorMessageRepositoryImpl(get()) }
        addFactory { GetLibraryUpdateErrorMessages(get()) }
        addFactory { DeleteLibraryUpdateErrorMessages(get()) }
        addFactory { InsertLibraryUpdateErrorMessages(get()) }

        addSingletonFactory<LibraryUpdateErrorRepository> { LibraryUpdateErrorRepositoryImpl(get()) }
        addFactory { GetLibraryUpdateErrors(get()) }
        addFactory { DeleteLibraryUpdateErrors(get()) }
        addFactory { InsertLibraryUpdateErrors(get()) }
    }
}
