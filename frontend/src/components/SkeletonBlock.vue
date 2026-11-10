<template>
  <div class="skeleton-block t-skel" aria-hidden="true">
    <div class="t-skel-skeleton is-pulsing"><span /></div>
  </div>
</template>

<style scoped>
.skeleton-block { min-height: 1em; height: 1em; border-radius: var(--radius-sm); overflow: hidden; }
.t-skel-skeleton > span { display: block; width: 100%; height: 100%; background: var(--color-surface-soft); }
</style>
