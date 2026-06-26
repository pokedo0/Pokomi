# Komikku

Komikku is an Android manga reader with local concepts for source browsing, author following, and translated tag metadata.

## Language

**Author Subscription**:
A followed browse query that groups manga results under one entry in the Following page. It can be a plain author/group name or an EhTagTranslation namespaced tag query.
_Avoid_: Favorite author

**Namespaced Tag Query**:
An EhTagTranslation tag search written as `namespace:keyword`, where the namespace identifies the tag category and the keyword is the translated term candidate.
_Avoid_: Author name, raw tag

**Library Author Grouping**:
A library grouping mode that creates virtual library sections from original manga author metadata instead of user categories.
_Avoid_: Following, author subscription

**Artist Namespace**:
The EhTagTranslation `artist` namespace for individual creators. Library author grouping prefers this namespace when the user chooses artist-based grouping.
_Avoid_: Author field, group

**Group Namespace**:
The EhTagTranslation `group` namespace for circles or creator groups. Library author grouping prefers this namespace when the user chooses group-based grouping.
_Avoid_: Artist field, author
