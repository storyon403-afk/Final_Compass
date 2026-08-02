<script setup>
defineProps({
  scene: { type: String, required: true }
})
</script>

<template>
  <div class="auth-transition-scene" :class="`scene-${scene}`" aria-hidden="true">
    <svg v-if="scene === 'tree'" class="scene-art tree-art" viewBox="0 0 1440 900" preserveAspectRatio="xMinYMax meet">
      <g class="draw-lines tree-lines" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round">
        <path pathLength="1" d="M95 900 C112 780 134 690 180 600 C220 522 251 436 270 322" />
        <path pathLength="1" d="M178 606 C124 560 85 504 57 438" />
        <path pathLength="1" d="M151 573 C126 503 130 438 151 374" />
        <path pathLength="1" d="M212 522 C272 467 316 405 338 328" />
        <path pathLength="1" d="M235 461 C204 400 191 337 197 270" />
        <path pathLength="1" d="M269 326 C325 286 366 231 388 164" />
        <path pathLength="1" d="M269 326 C248 263 249 202 268 142" />
        <path pathLength="1" d="M57 438 C27 390 18 346 22 303 M57 438 C77 388 92 344 91 294" />
        <path pathLength="1" d="M151 374 C120 329 111 283 119 234 M151 374 C178 330 186 281 180 230" />
        <path pathLength="1" d="M338 328 C374 292 402 249 414 199 M338 328 C330 271 340 220 370 176" />
        <path pathLength="1" d="M197 270 C163 236 145 198 141 156 M197 270 C218 229 224 189 214 146" />
        <path pathLength="1" d="M388 164 C421 137 443 103 454 65 M388 164 C379 122 384 85 402 50" />
        <path pathLength="1" d="M268 142 C241 111 228 78 230 42 M268 142 C291 106 299 72 294 36" />
      </g>
      <g class="tree-nodes" fill="currentColor">
        <circle cx="57" cy="438" r="4" /><circle cx="151" cy="374" r="4" /><circle cx="338" cy="328" r="4" />
        <circle cx="197" cy="270" r="4" /><circle cx="388" cy="164" r="4" /><circle cx="268" cy="142" r="4" />
      </g>
      <text class="diagram-label label-root" x="110" y="855">root</text>
      <text class="diagram-label" x="284" y="308">branch / 03</text>
    </svg>

    <svg v-else-if="scene === 'waves'" class="scene-art waves-art" viewBox="0 0 1440 900" preserveAspectRatio="none">
      <g class="draw-lines wave-lines" fill="none" stroke="currentColor" stroke-linecap="round">
        <path pathLength="1" d="M1440 620 C1290 548 1170 705 1015 630 C860 554 740 715 575 638 C410 560 272 704 0 628" />
        <path pathLength="1" d="M1440 688 C1280 615 1140 765 980 692 C820 617 675 770 512 697 C349 623 215 758 0 690" />
        <path pathLength="1" d="M1440 754 C1288 695 1154 826 994 756 C834 687 702 824 535 761 C368 696 223 817 0 758" />
        <path pathLength="1" d="M1440 816 C1280 770 1135 877 972 821 C809 765 664 879 502 826 C340 773 183 866 0 829" />
      </g>
      <g class="foam-lines draw-lines" fill="none" stroke="currentColor" stroke-linecap="round">
        <path pathLength="1" d="M1240 595 q-34-34-70 0 q-34 34-70 0 M890 650 q-28-27-57 0 q-28 27-57 0 M385 613 q-31-30-63 0 q-31 30-63 0" />
        <path pathLength="1" d="M1325 720 q-25-22-50 0 q-25 22-50 0 M690 744 q-24-22-49 0 q-24 22-49 0 M205 747 q-23-21-47 0 q-23 21-47 0" />
      </g>
      <line class="horizon-line" x1="0" y1="574" x2="1440" y2="574" pathLength="1" />
      <text class="diagram-label tide-label" x="80" y="790">tide / study / repeat</text>
    </svg>

    <svg v-else class="scene-art vine-art" viewBox="0 0 1440 900" preserveAspectRatio="xMinYMin meet">
      <g class="draw-lines vine-lines" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round">
        <path pathLength="1" d="M-20 58 C110 42 104 160 222 142 C340 124 295 272 430 252 C522 238 522 340 617 352" />
        <path pathLength="1" d="M91 76 C89 129 62 164 20 184 M198 143 C185 90 204 51 249 21" />
        <path pathLength="1" d="M321 182 C373 165 410 181 434 221 M425 253 C397 302 406 347 446 383" />
        <path pathLength="1" d="M514 280 C555 247 599 244 642 269 M557 325 C520 367 515 414 538 459" />
      </g>
      <g class="vine-leaves draw-shapes" fill="none" stroke="currentColor">
        <path pathLength="1" d="M87 128 C42 107 39 70 76 57 C108 76 113 105 87 128Z" />
        <path pathLength="1" d="M201 98 C171 67 185 31 224 26 C248 54 238 86 201 98Z" />
        <path pathLength="1" d="M365 168 C371 122 408 109 438 137 C431 174 401 188 365 168Z" />
        <path pathLength="1" d="M410 316 C365 325 347 358 370 390 C410 387 430 355 410 316Z" />
        <path pathLength="1" d="M566 272 C577 229 615 219 641 250 C630 287 600 298 566 272Z" />
      </g>
      <g class="flower flower-one" transform="translate(260 152)">
        <circle cx="0" cy="-22" r="17" /><circle cx="21" cy="-7" r="17" /><circle cx="13" cy="18" r="17" />
        <circle cx="-13" cy="18" r="17" /><circle cx="-21" cy="-7" r="17" /><circle class="flower-core" cx="0" cy="0" r="7" />
      </g>
      <g class="flower flower-two" transform="translate(456 250) scale(.8)">
        <circle cx="0" cy="-22" r="17" /><circle cx="21" cy="-7" r="17" /><circle cx="13" cy="18" r="17" />
        <circle cx="-13" cy="18" r="17" /><circle cx="-21" cy="-7" r="17" /><circle class="flower-core" cx="0" cy="0" r="7" />
      </g>
      <g class="flower flower-three" transform="translate(619 352) scale(.65)">
        <circle cx="0" cy="-22" r="17" /><circle cx="21" cy="-7" r="17" /><circle cx="13" cy="18" r="17" />
        <circle cx="-13" cy="18" r="17" /><circle cx="-21" cy="-7" r="17" /><circle class="flower-core" cx="0" cy="0" r="7" />
      </g>
      <text class="diagram-label vine-label" x="70" y="226">grow gently</text>
    </svg>
  </div>
</template>
