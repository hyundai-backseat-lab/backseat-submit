package io.backseatlab.playground.game

import io.backseatlab.playground.game.api.Difficulty

/**
 * 관찰 미션 하나. [answers] 중 하나라도 아이 말에 들어 있으면 정답이다.
 * [right]·[wrong]은 정답·오답 예시 3개씩이고 테스트가 전부 돌려 본다.
 */
class Mission(
    val id: String,
    val level: Difficulty,
    val title: String,
    val prompt: String,
    val hint: String,
    val answers: List<String>,
    val right: List<String> = emptyList(),
    val wrong: List<String> = emptyList(),
    val hasCard: Boolean = true,
)

// 데이터는 코드에 둔다: createGame에 Context가 없어 assets를 읽을 수 없기 때문(decisions D-022).
private fun easy(
    n: Int,
    title: String,
    hint: String,
    answers: List<String>,
    right: List<String>,
    wrong: List<String>,
) = Mission("easy-$n", Difficulty.EASY, title, "창밖에서 $title 찾아볼까요?", hint, answers, right, wrong)

private fun normal(
    n: Int,
    ch: String,
    right: List<String>,
    wrong: List<String>,
) = Mission(
    "normal-$n",
    Difficulty.NORMAL,
    "'$ch' 자",
    "'$ch' 자가 들어간 간판을 찾아볼까요?",
    "가게 이름에 '$ch' 자가 있어요. 간판을 천천히 봐요",
    listOf(ch),
    right,
    wrong,
)

private fun hard(
    n: Int,
    word: String,
    right: List<String>,
    wrong: List<String>,
    extra: List<String> = emptyList(),
) = Mission(
    "hard-$n",
    Difficulty.HARD,
    "'$word'",
    "'$word'라는 단어가 들어간 간판을 찾아볼까요?",
    "'$word'는 가게 이름에 자주 나와요",
    listOf(word) + extra,
    right,
    wrong,
)

val OBSERVE_MISSIONS: List<Mission> =
    listOf(
        easy(
            0,
            "빨간 차",
            "빨간색을 눈여겨봐요",
            listOf("빨간", "빨강"),
            listOf("빨간 차 있다", "빨강 차", "저기 빨간 거"),
            listOf("파란 차", "트럭", "버스"),
        ),
        easy(1, "버스", "크고 길쭉한 차예요", listOf("버스"), listOf("버스다", "저기 버스", "버스 있어요"), listOf("택시", "자전거", "트럭")),
        easy(2, "신호등", "길 건너는 곳에 있어요", listOf("신호"), listOf("신호등 있다", "저기 신호등", "신호"), listOf("가로등", "표지판", "나무")),
        easy(3, "나무", "초록색 잎이 달려 있어요", listOf("나무"), listOf("나무 봤다", "큰 나무", "나무다"), listOf("꽃", "풀", "건물")),
        easy(
            4,
            "건물",
            "높고 네모난 것이에요",
            listOf("건물", "빌딩", "아파트"),
            listOf("건물 있어", "저기 아파트", "빌딩이다"),
            listOf("산", "다리", "나무"),
        ),
        easy(5, "자전거", "바퀴가 두 개예요", listOf("자전거"), listOf("자전거 탄다", "저기 자전거", "자전거다"), listOf("오토바이", "버스", "킥보드")),
        normal(0, "국", listOf("국밥집", "국수", "국"), listOf("치킨집", "분식", "카페")),
        normal(1, "약", listOf("약국", "약 간판", "한약방"), listOf("병원", "치과", "편의점")),
        normal(2, "집", listOf("치킨집", "고깃집", "집"), listOf("카페", "마트", "은행")),
        normal(3, "마", listOf("마트", "마켓", "마"), listOf("은행", "약국", "빵집")),
        normal(4, "빵", listOf("빵집", "빵 가게", "제과빵"), listOf("치킨", "카페", "마트")),
        normal(5, "병", listOf("병원", "병", "소아병원"), listOf("약국", "치과", "은행")),
        hard(0, "카페", listOf("카페다", "커피 간판", "커피숍 카페"), listOf("치킨", "마트", "은행"), listOf("커피")),
        hard(1, "치킨", listOf("치킨집", "치킨 있다", "저기 치킨"), listOf("피자", "카페", "햄버거")),
        hard(2, "편의점", listOf("편의점이다", "저기 편의점", "편의점 봤어"), listOf("마트", "약국", "카페")),
        hard(3, "주유소", listOf("주유소다", "저기 주유소", "주유소 있어"), listOf("정비소", "카페", "마트")),
        hard(4, "약국", listOf("약국이다", "저기 약국", "약국 봤어"), listOf("병원", "치과", "마트")),
        hard(5, "은행", listOf("은행이다", "저기 은행", "은행 봤어"), listOf("우체국", "마트", "카페")),
    )
