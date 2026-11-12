package com.example.epubtest.reader

import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 더미 데이터: Figma "데미안 리더" 화면용 체험 텍스트를 담은 EPUB 3 파일을 런타임에 생성한다.
 *
 * 실제 서비스에서는 서버/에셋에서 받은 .epub 파일 경로만 [ReaderViewModel]에 넘기면 되고,
 * 이 파일은 Readium이 "진짜 EPUB 파일"을 열 수 있는지 확인하기 위한 용도다.
 * (본문은 Figma 시안 문구 + 체험용 재구성 텍스트이며 원작 번역이 아님)
 */
object DummyBook {

    data class BookCharacter(val name: String, val initial: String, val avatarColor: Long)

    data class Chapter(
        val title: String,
        val paragraphs: List<String>,
        val characters: List<BookCharacter>,
    )

    const val TITLE = "데미안"
    const val AUTHOR = "헤르만 헤세"
    private const val VERSION = 4 // 내용을 바꾸면 올려서 캐시된 파일을 다시 만든다.

    private val SINCLAIR = BookCharacter("싱클레어", "S", 0xFFCFDCCB)
    private val KROMER = BookCharacter("크로머", "K", 0xFFDCCFC2)
    private val DEMIAN = BookCharacter("데미안", "D", 0xFFC9D2E3)

    val chapters: List<Chapter> = listOf(
        Chapter(
            title = "거짓말과 질문",
            paragraphs = listOf(
                "내가 열 살 무렵이던 해의 늦가을, 우리 동네 아이들은 학교가 끝나면 늘 강가의 낡은 다리 밑에 모였다. 그곳은 어른들의 눈이 닿지 않는 곳이었고, 그래서 우리는 그곳을 우리만의 나라처럼 여겼다.",
                "우리 집은 밝은 집이었다. 아버지는 저녁마다 신문을 읽었고, 어머니는 식탁에 늘 깨끗한 식탁보를 깔았다. 누나들은 피아노를 치거나 성경 구절을 외웠다. 그 집 안에서는 모든 것이 제자리에 있었고, 무엇이 옳고 무엇이 그른지 분명했다.",
                "그러나 대문 밖으로 한 걸음만 나서면 또 다른 세계가 있었다. 술 냄새가 나는 골목, 고함을 지르는 하숙집 여자, 이유 없이 싸우는 형들. 나는 그 두 세계 사이를 매일 오가며, 어느 쪽이 진짜 세상인지 알 수 없을 때가 많았다.",
                "그날 다리 밑에는 이웃 학교의 프란츠 크로머도 와 있었다. 그는 우리보다 두세 살 많았고, 재봉사의 아들이었으며, 어른처럼 침을 뱉고 어른처럼 웃었다. 아이들은 그를 무서워하면서도 그의 곁에 있고 싶어 했다.",
                "아이들이 저마다 자기 이야기를 꺼냈다. 누구는 이웃집 개를 혼내 준 이야기를, 누구는 선생님 몰래 교실 창문으로 빠져나간 이야기를 했다. 이야기가 하나씩 끝날 때마다 크로머는 코웃음을 치거나 고개를 끄덕였다.",
                "나는 뒤처지고 싶지 않았다. 내 차례가 다가올수록 가슴이 두근거렸다. 내게는 그들처럼 내세울 만한 모험이 하나도 없었다. 그래서 나는 실제로 겪지 않은 일을 마치 내 이야기처럼 말하기 시작했다.",
                "나는 어느 날 밤 친구와 함께 방앗간 옆 과수원에 몰래 들어가 사과를 한 자루나 따 왔다고 말했다. 개가 짖었고, 우리는 담을 넘어 도망쳤으며, 그 사과는 아직 우리 집 창고에 숨겨 두었다고 덧붙였다. 말을 할수록 이야기는 점점 그럴듯해졌다.",
                "크로머가 내 쪽으로 몸을 기울였다. “그거 정말이야?” 그 짧은 물음에, 방금까지 쉽게 나오던 말이 목에 걸리는 것 같았다.",
                "나는 곧바로 대답하지 못했다. 지어낸 이야기라고 말할지, 조금 더 버텨볼지 망설였다. 크로머는 여전히 내 대답을 기다리고 있었다. 다른 아이들도 숨을 죽인 채 나를 바라보았다.",
                "“그래, 정말이야.” 내 입에서 나온 목소리는 내 것이 아닌 것처럼 낯설었다. 크로머는 천천히 웃었다. 그 웃음은 칭찬 같기도 했고, 무언가를 손에 쥔 사람의 웃음 같기도 했다.",
                "“그 과수원 주인이 누가 사과를 훔쳐 갔는지 알려 주는 사람한테 돈을 준다더라.” 크로머가 말했다. “2마르크라던가.” 그는 그 말을 아무렇지 않게, 마치 날씨 이야기를 하듯 했다.",
                "그 순간 나는 내가 무슨 일을 저질렀는지 깨달았다. 다리 밑의 공기가 갑자기 차갑게 느껴졌다. 강물 소리가 귀에 크게 울렸고, 다른 아이들의 얼굴이 하나둘 흐릿해졌다.",
                "아이들이 하나씩 집으로 돌아간 뒤에도 크로머는 나를 붙잡아 두었다. 그는 내 어깨에 팔을 두르고, 다정한 형처럼 낮은 목소리로 말했다. “걱정 마. 나는 아무한테도 말 안 해. 네가 나한테 잘하기만 하면.”",
                "그날 저녁 집으로 돌아가는 길은 유난히 길었다. 대문을 열자 따뜻한 불빛과 수프 냄새가 나를 맞았지만, 나는 그 안으로 들어갈 자격이 없는 사람이 된 것 같았다. 밝은 세계의 문턱에서 나는 처음으로 그곳이 내 것이 아닐지도 모른다고 생각했다.",
            ),
            characters = listOf(SINCLAIR, KROMER),
        ),
        Chapter(
            title = "갚을 수 없는 빚",
            paragraphs = listOf(
                "크로머는 그날 이후로 나를 자주 불러냈다. 내가 지어낸 이야기는 어느새 그가 쥔 약점이 되어 있었다.",
                "처음에 그가 원한 것은 돈이었다. 나는 저금통을 깨뜨려 몇 푼 안 되는 동전을 모아 그에게 건넸다. 그는 동전을 손바닥 위에서 굴려 보더니 실망한 듯 혀를 찼다. “이걸로는 모자라. 다음에는 더 가져와.”",
                "그 뒤로 나는 거짓말을 하나씩 더 쌓아 갔다. 학용품을 산다며 어머니에게 돈을 받았고, 심부름 거스름돈을 조금씩 떼어 냈다. 처음의 거짓말 하나를 지키기 위해 수많은 작은 거짓말이 필요했다.",
                "집으로 돌아오는 골목은 평소보다 길게 느껴졌다. 창문마다 켜진 불빛이 나만 빼고 모두 평온하다고 말하는 것 같았다.",
                "저녁 식탁에서 나는 아무 일도 없는 척 웃었다. 그러나 숟가락을 드는 손끝까지 비밀의 무게가 내려앉아 있었다. 아버지가 무심코 내 이름을 부를 때마다 나는 들킨 것처럼 움찔했다.",
                "밤이 되면 더 괴로웠다. 침대에 누우면 천장에 크로머의 얼굴이 떠올랐다. 꿈속에서 그는 내 방 창문을 두드렸고, 나는 소리를 지르려 해도 목소리가 나오지 않았다. 아침에 눈을 뜨면 베개가 땀에 젖어 있었다.",
                "크로머는 휘파람으로 나를 불렀다. 우리 집 담장 밖에서 짧고 날카로운 휘파람 소리가 들리면, 나는 무엇을 하고 있든 밖으로 나가야 했다. 그 소리는 곧 내 하루를 지배하는 신호가 되었다.",
                "어느 날 그는 돈 대신 다른 것을 요구했다. 누나를 데리고 나오라는 것이었다. 나는 그 말을 듣는 순간 온몸이 굳었다. 그것만은 할 수 없었다. 그러나 거절할 용기도 나지 않았다.",
                "나는 아버지에게 모든 것을 털어놓는 장면을 수없이 상상했다. 아버지의 서재 문을 두드리고, 고개를 숙인 채 처음부터 끝까지 이야기하는 나. 그러나 상상은 늘 문 앞에서 멈추었다. 손잡이를 돌릴 힘이 내게는 없었다.",
                "한번은 아버지가 진흙 묻은 내 신발을 보고 꾸짖은 적이 있었다. 나는 고개를 숙이고 그 꾸지람을 들으면서 이상한 감정을 느꼈다. 아버지는 내가 신발을 더럽힌 것만 알 뿐, 내가 얼마나 더 깊은 곳에 빠져 있는지는 모르고 있었다.",
                "그때 처음으로 나는 아버지가 모든 것을 아는 사람이 아니라는 사실을 깨달았다. 그것은 해방감이기도 했고, 동시에 깊은 외로움이기도 했다. 밝은 세계의 기둥에 처음으로 금이 간 순간이었다.",
                "나는 점점 말수가 줄었고, 얼굴은 창백해졌다. 어머니는 내 이마에 손을 얹어 보며 걱정했고, 의사를 부르자고 했다. 나는 아프다는 핑계로 며칠을 침대에서 보냈다. 차라리 정말로 병이 들어 모든 것이 끝나기를 바란 날도 있었다.",
                "창밖으로 계절이 바뀌고 있었다. 마당의 밤나무 잎이 모두 떨어지고, 첫눈이 내렸다. 세상은 아무 일 없다는 듯 흘러갔지만, 나는 그 흐름에서 떨어져 나와 홀로 얼어붙어 있었다.",
            ),
            characters = listOf(SINCLAIR, KROMER),
        ),
        Chapter(
            title = "낯선 아이",
            paragraphs = listOf(
                "새 학기에 전학 온 아이는 어딘가 어른 같은 눈빛을 하고 있었다. 아이들은 그를 데미안이라고 불렀다.",
                "그는 과부인 어머니와 함께 우리 동네 외곽의 큰 집으로 이사 왔다고 했다. 그 집에 대해서는 여러 소문이 돌았다. 어머니가 교회에 나오지 않는다는 이야기도 있었고, 그들이 먼 나라에서 왔다는 이야기도 있었다.",
                "데미안은 나보다 몇 살 위였지만 상급반이 아닌 우리 반 옆 교실에서 함께 수업을 들었다. 그는 수업 시간에도 좀처럼 흐트러지지 않았다. 선생님이 질문을 던지면 그는 잠시 생각하다가 아무도 예상하지 못한 대답을 내놓곤 했다.",
                "어느 날 종교 수업에서 카인과 아벨 이야기가 나왔다. 선생님은 동생을 죽인 카인이 하늘로부터 표식을 받아 영원히 떠돌게 되었다고 설명했다. 아이들은 모두 고개를 끄덕였고, 나 역시 그 이야기를 의심해 본 적이 없었다.",
                "그는 수업이 끝난 뒤 내 옆을 나란히 걸으며, 마치 오래전부터 알던 사람처럼 말을 걸어왔다. “오늘 그 이야기, 너는 어떻게 생각해?”",
                "나는 무슨 대답을 해야 할지 몰라 머뭇거렸다. 그가 웃으며 말을 이었다. “나는 좀 다르게 생각해. 어쩌면 그 표식은 벌이 아니었을지도 몰라. 남들과 다른 무언가를 가진 사람에게, 사람들이 붙인 이름이었을지도.”",
                "그의 말은 낯설고 위험하게 들렸다. 그러나 이상하게도 그 말이 내 안 어딘가에 단단히 박혔다. 집으로 돌아오는 내내 나는 그 말을 곱씹었다.",
                "며칠 뒤 그는 다시 내 옆에 나타났다. 우리는 강가를 따라 걸었다. 그는 새에 대해, 별에 대해, 사람의 얼굴에 대해 이야기했다. 사람의 얼굴을 오래 바라보면 그 사람이 무엇을 숨기고 있는지 보인다고 했다.",
                "“너, 요즘 무언가를 두려워하고 있지?” 그 질문은 놀라울 만큼 정확해서, 나는 걸음을 멈출 수밖에 없었다.",
                "나는 아니라고 말하려 했다. 그러나 그의 눈을 마주한 순간 입이 열리지 않았다. 그는 재촉하지 않았다. 그저 내가 스스로 말할 때까지 조용히 기다려 주었다.",
                "결국 나는 모든 것을 털어놓았다. 다리 밑의 거짓말, 과수원 이야기, 크로머의 휘파람, 저금통과 거스름돈까지. 말을 하는 동안 목이 메었고, 몇 번이나 말을 멈추었다.",
                "데미안은 끝까지 듣고 나서 한참 동안 아무 말도 하지 않았다. 그리고 마침내 이렇게 말했다. “네가 과수원에서 사과를 훔치지 않았다는 건 알아. 그런데 너는 그걸 증명할 수 없어서 두려운 거지.”",
                "그는 크로머가 어떤 사람인지 아느냐고 물었다. 나는 고개를 저었다. “두려움을 이용하는 사람은 상대가 두려워하는 동안만 힘이 있어.” 그가 말했다. “네가 두려워하지 않게 되면, 그는 아무것도 아니야.”",
                "그날 밤 나는 오랜만에 깊이 잠들었다. 문제는 하나도 해결되지 않았지만, 누군가 내 비밀을 알고도 나를 경멸하지 않았다는 사실만으로 가슴 한쪽이 가벼워졌다.",
            ),
            characters = listOf(SINCLAIR, DEMIAN),
        ),
        Chapter(
            title = "표식",
            paragraphs = listOf(
                "데미안은 교과서에 실린 오래된 이야기를 전혀 다른 방식으로 읽어 보자고 했다. 낙인이 꼭 벌만은 아닐지도 모른다고.",
                "그는 이야기를 뒤집어 보는 데 능했다. 선생님이 들려준 우화나 성경 구절을 가지고 와서는, 그 안에서 아무도 보지 못한 틈을 찾아냈다. 그가 그 틈을 손가락으로 가리키면, 굳건해 보이던 이야기가 조금씩 흔들렸다.",
                "나는 처음으로, 배운 대로가 아니라 스스로 생각해 본다는 것이 얼마나 낯설고도 짜릿한 일인지 알게 되었다.",
                "그 무렵 나는 데미안을 관찰하는 버릇이 생겼다. 그는 때때로 수업 중에 완전히 다른 사람처럼 보였다. 얼굴에서 나이가 사라지고, 아이도 어른도 아닌 무엇인가가 되어 먼 곳을 바라보았다. 그럴 때면 나는 그에게 말을 걸 수 없었다.",
                "며칠 뒤, 크로머는 더 이상 나를 부르지 않았다. 담장 밖의 휘파람 소리도 들리지 않았다. 처음에는 그것이 또 다른 함정일까 봐 불안했다. 그러나 하루가 가고 이틀이 가도 그는 나타나지 않았다.",
                "골목에서 우연히 크로머와 마주친 날이 있었다. 그는 나를 보자 고개를 돌리고 반대편으로 걸어갔다. 그 모습이 너무 낯설어서 나는 한참 동안 그 자리에 서 있었다.",
                "무슨 일이 있었는지 묻자 데미안은 그저 조용히 웃기만 했다. “그냥 이야기를 좀 나눴어. 그 애도 자기가 무엇을 하고 있는지 알 필요가 있었거든.” 그 이상은 아무리 물어도 대답하지 않았다.",
                "나는 자유로워졌다. 그런데 이상하게도 그 자유를 어떻게 써야 할지 알 수 없었다. 나는 서둘러 밝은 세계로 돌아갔다. 부모님께 다정하게 굴었고, 누나들과 함께 노래를 불렀으며, 교회에서 열심히 기도했다.",
                "그리고 나는 데미안을 피하기 시작했다. 그에게 고마웠지만, 동시에 그가 두려웠다. 그는 내가 다시 덮어 두고 싶은 질문들을 너무 많이 알고 있었다. 그의 곁에 있으면 밝은 세계로 완전히 돌아갈 수 없을 것 같았다.",
                "그 사실이 부끄러웠다. 나를 구해 준 사람에게서 등을 돌리는 것이 옳지 않다는 걸 알았다. 그러나 그때의 나는 아직 너무 어렸고, 다시 찾은 평온을 놓치고 싶지 않았다.",
                "시간이 흐르면서 데미안과 나는 같은 학교에 다니면서도 거의 말을 섞지 않게 되었다. 복도에서 마주치면 그는 가볍게 고개를 끄덕였다. 그 눈빛에는 원망도 서운함도 없었다. 마치 언젠가 내가 다시 찾아올 것을 알고 있다는 듯했다.",
                "견진 성사 수업이 시작되었을 때, 우연히 데미안과 나는 같은 반이 되었다. 목사님이 다시 카인 이야기를 꺼냈을 때, 나는 저도 모르게 그의 얼굴을 바라보았다. 그도 나를 보고 있었다.",
                "그 짧은 눈맞춤만으로 나는 알았다. 내가 아무리 밝은 세계로 도망쳐도, 그가 내 안에 심어 놓은 질문은 사라지지 않았다는 것을. 그것은 이미 나의 일부가 되어 있었다.",
            ),
            characters = listOf(SINCLAIR, DEMIAN),
        ),
        Chapter(
            title = "알을 깨고",
            paragraphs = listOf(
                "시간이 흘러 나는 집을 떠나 낯선 도시의 기숙사에서 지내게 되었다. 익숙한 것들과 멀어질수록 내 안의 질문은 더 또렷해졌다.",
                "기숙사 생활은 생각보다 외로웠다. 나는 친구를 사귀는 데 서툴렀고, 수업에도 흥미를 붙이지 못했다. 저녁이면 혼자 도시의 오래된 골목을 걸으며 시간을 보냈다.",
                "한동안 나는 술집을 드나들며 방황했다. 나이 많은 학생들과 어울려 밤늦도록 떠들고, 아무것도 아닌 일에 큰소리로 웃었다. 그러나 그 소란이 끝나고 혼자 남으면 마음은 이전보다 더 텅 비어 있었다.",
                "그런 나날 속에서 어느 봄날 공원에서 한 여인을 보았다. 이름도 모르는 그녀의 옆모습은 이상하게도 나를 붙잡았다. 나는 다시 그녀를 만나지 못했지만, 그날 이후 술집에 발길을 끊었다.",
                "나는 방 안에 틀어박혀 그림을 그리기 시작했다. 처음에는 그녀의 얼굴을 그리려 했지만, 아무리 그려도 그 얼굴은 나오지 않았다. 대신 종이 위에는 매번 조금씩 다른 얼굴이 나타났다. 남자 같기도 하고 여자 같기도 한, 아이 같기도 하고 노인 같기도 한 얼굴이었다.",
                "어느 밤 그 그림을 한참 바라보다가 나는 숨을 멈추었다. 그것은 데미안의 얼굴을 닮아 있었다. 아니, 어쩌면 그것은 내 얼굴이기도 했다.",
                "나는 밤마다 한 마리 새를 그렸다. 껍질을 깨고 나오려 애쓰는, 아직 이름 없는 새였다.",
                "새의 머리는 날카로웠고, 반쯤 깨진 껍질 사이로 날개 끝이 보였다. 나는 그 그림을 데미안이 살던 옛 주소로 부쳤다. 답장을 기대하지는 않았다. 그저 누군가에게 그것을 보여 주어야 할 것 같았다.",
                "몇 주 뒤, 수업 시간에 펼친 책갈피 사이에서 작은 쪽지 하나를 발견했다. 누가 넣었는지는 알 수 없었다. 거기에는 짧은 몇 줄이 적혀 있었다. 새는 자신이 갇힌 세계를 깨뜨려야만 날 수 있다는, 그런 뜻의 문장이었다.",
                "나는 그 쪽지를 몇 번이고 다시 읽었다. 그것이 데미안의 대답이라는 것을 나는 의심하지 않았다. 그는 여전히 어딘가에서 나를 지켜보고 있었다.",
                "그 뒤로 나는 오르간 연주자 한 사람을 알게 되었다. 그는 교회가 비어 있는 저녁마다 혼자 오르간을 연주했다. 나는 교회 뒤편 의자에 앉아 그 소리를 들었다. 그와 나는 불과 꿈과 오래된 신화에 대해 긴 이야기를 나누었다.",
                "그는 많은 것을 알려 주었지만, 결국 나는 그에게서도 떠나야 했다. 누군가의 말을 따라가는 것만으로는 내 길을 찾을 수 없다는 것을 그 무렵 어렴풋이 깨닫고 있었다.",
                "그림을 완성한 날, 나는 비로소 알았다. 누군가 대신 깨 주는 세계는 없으며, 그 첫 균열은 언제나 내 안에서 시작된다는 것을.",
                "창밖으로 새벽이 밝아 오고 있었다. 나는 그림을 벽에 걸고 오랫동안 바라보았다. 다리 밑의 거짓말에서 시작된 길이 여기까지 이어져 있었다. 그 길은 아직 끝나지 않았고, 나는 처음으로 그 사실이 두렵지 않았다.",
            ),
            characters = listOf(SINCLAIR, DEMIAN),
        ),
    )

    fun charactersFor(chapterIndex: Int): List<BookCharacter> =
        chapters.getOrNull(chapterIndex)?.characters ?: listOf(SINCLAIR)

    /** cacheDir 아래에 EPUB을 만들고(이미 있으면 재사용) 파일을 돌려준다. */
    fun writeEpub(dir: File): File {
        val file = File(dir, "demian-dummy-v$VERSION.epub")
        if (file.exists() && file.length() > 0) return file

        val tmp = File(dir, file.name + ".tmp")
        ZipOutputStream(tmp.outputStream().buffered()).use { zip ->
            // EPUB 규격: mimetype은 첫 엔트리 + 무압축(STORED)
            val mimetype = "application/epub+zip".toByteArray()
            zip.putNextEntry(
                ZipEntry("mimetype").apply {
                    method = ZipEntry.STORED
                    size = mimetype.size.toLong()
                    compressedSize = mimetype.size.toLong()
                    crc = CRC32().apply { update(mimetype) }.value
                }
            )
            zip.write(mimetype)
            zip.closeEntry()

            fun put(path: String, content: String) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }

            put("META-INF/container.xml", CONTAINER_XML)
            put("OEBPS/content.opf", opf())
            put("OEBPS/nav.xhtml", nav())
            put("OEBPS/style.css", CSS)
            chapters.forEachIndexed { i, chapter -> put("OEBPS/ch${i + 1}.xhtml", chapterXhtml(i, chapter)) }
        }
        tmp.renameTo(file)
        return file
    }

    private fun String.xml(): String = this
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private fun chapterXhtml(index: Int, chapter: Chapter): String = buildString {
        append(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE html>
            <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" xml:lang="ko" lang="ko">
            <head>
              <meta charset="UTF-8"/>
              <title>${chapter.title.xml()}</title>
              <link rel="stylesheet" type="text/css" href="style.css"/>
            </head>
            <body>
              <section epub:type="chapter" id="ch${index + 1}">
                <div class="label">${index + 1}장</div>
                <h1 class="title">${chapter.title.xml()}</h1>
            """.trimIndent()
        )
        append('\n')
        chapter.paragraphs.forEachIndexed { p, text ->
            append("    <p id=\"c${index + 1}-p${p + 1}\">${text.xml()}</p>\n")
        }
        append(
            """
              </section>
            </body>
            </html>
            """.trimIndent()
        )
    }

    private fun opf(): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="bookid" xml:lang="ko">
          <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
            <dc:identifier id="bookid">urn:uuid:2f3c9c4e-6d1a-4c2b-9d61-epubtest-demian</dc:identifier>
            <dc:title>$TITLE</dc:title>
            <dc:creator>$AUTHOR</dc:creator>
            <dc:language>ko</dc:language>
            <meta property="dcterms:modified">2026-11-08T00:00:00Z</meta>
          </metadata>
          <manifest>
            <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
            <item id="css" href="style.css" media-type="text/css"/>
        ${chapters.indices.joinToString("\n") { "            <item id=\"ch${it + 1}\" href=\"ch${it + 1}.xhtml\" media-type=\"application/xhtml+xml\"/>" }}
          </manifest>
          <spine>
        ${chapters.indices.joinToString("\n") { "            <itemref idref=\"ch${it + 1}\"/>" }}
          </spine>
        </package>
    """.trimIndent()

    private fun nav(): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <!DOCTYPE html>
        <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" xml:lang="ko" lang="ko">
        <head><meta charset="UTF-8"/><title>목차</title></head>
        <body>
          <nav epub:type="toc" id="toc">
            <ol>
        ${chapters.mapIndexed { i, c -> "              <li><a href=\"ch${i + 1}.xhtml\">${c.title.xml()}</a></li>" }.joinToString("\n")}
            </ol>
          </nav>
        </body>
        </html>
    """.trimIndent()

    private val CONTAINER_XML = """
        <?xml version="1.0" encoding="UTF-8"?>
        <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
          <rootfiles>
            <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
          </rootfiles>
        </container>
    """.trimIndent()

    // 색상은 Readium 테마(EpubPreferences)가 주입하므로 여기서는 크기/간격만 정의한다.
    private val CSS = """
        .label { text-align: center; font-size: 0.75em; letter-spacing: 0.1em; opacity: 0.5; margin: 0.4em 0 1.1em 0; }
        h1.title { text-align: center; font-size: 1.4em; font-weight: 500; margin: 0 0 1.8em 0; line-height: 1.4; }
    """.trimIndent()
}
